package grails.plugins.mail.graph.auth

import com.github.scribejava.core.oauth.OAuth20Service
import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.sender.SessionStateStoreService
import grails.plugins.mail.graph.token.InMemoryReaderTokenStoreService
import grails.plugins.mail.oauth.MailOAuthService
import grails.plugins.mail.oauth.TenantMailConfigResolverService
import grails.plugins.mail.oauth.token.MemoryTokenStore
import grails.plugins.mail.tenant.TenantOAuthContext
import grails.plugins.tenant.TenantIdContext
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

/**
 * Exercises real ScribeJava OAuth20Service construction (no network) for every auth mode in the
 * multi-tenant sender (per-tenant ConfigObject) and the reader (GraphConfig):
 * secret/interactive, secret/daemon and certificate/daemon.
 */
class AuthModeConfigurationSpec extends Specification implements ServiceUnitTest<MailOAuthService> {

    private static final String CALLBACK = 'https://app.example.com/mailOAuth/callback'
    private static final String SCOPE = 'https://graph.microsoft.com/.default'

    TenantMailConfigResolverService resolver

    def setup() {
        resolver = Mock(TenantMailConfigResolverService)
        service.tenantMailConfigResolverService = resolver
        service.tokenStore = new MemoryTokenStore()
        service.stateStoreService = Mock(SessionStateStoreService)
    }

    def cleanup() {
        TenantIdContext.clear()
    }

    /** Global (non per-tenant) settings read through MailOAuthUtil / Holders. */
    private void globals(boolean daemon) {
        config.merge([grails: [mail: [oAuth: [daemon: daemon, api_scope: SCOPE, callback_url: CALLBACK, 'redirect.uri': '/mailOAuth/index']]]])
    }

    private static ConfigObject tenantCfg(Map oAuth) {
        ConfigObject cfg = new ConfigObject()
        cfg.username = 'mailbox@example.com'
        cfg.oAuth.enabled = true
        cfg.oAuth.client_id = 'client-id'
        cfg.oAuth.tenant_id = 'entra-tenant'
        oAuth.each { k, v -> cfg.oAuth[k] = v }
        cfg
    }

    void "sender builds ScribeJava service with secret in interactive mode and produces auth-code URL"() {
        given:
        globals(false)
        TenantIdContext.setTenantId(1L)
        ConfigObject cfg = tenantCfg(secret_val: 'top-secret')
        resolver.resolve(1L) >> cfg

        when:
        TenantOAuthContext ctx = service.buildContext(cfg, 1L)
        String url = service.generateAuthCodeURL()

        then:
        ctx.authStrategy instanceof SecretKeyStrategy
        !ctx.daemon
        ctx.oauthService.apiKey == 'client-id'
        ctx.oauthService.apiSecret == 'top-secret'
        ctx.oauthService.callback == CALLBACK
        url.startsWith('https://login.microsoftonline.com/entra-tenant/oauth2/v2.0/authorize')
        url.contains('client_id=client-id')
        url.contains('prompt=login')
        url.contains('redirect_uri=')
        url.contains('state=tenantId%253D1')  // tenant id is carried in the (URL-encoded) OAuth state
    }

    void "sender in secret daemon mode still builds ScribeJava service and returns admin consent URL"() {
        given:
        globals(true)
        TenantIdContext.setTenantId(2L)
        ConfigObject cfg = tenantCfg(secret_val: 'top-secret')
        resolver.resolve(2L) >> cfg

        when:
        TenantOAuthContext ctx = service.buildContext(cfg, 2L)
        String url = service.generateAuthCodeURL()

        then:
        ctx.authStrategy instanceof SecretKeyStrategy
        ctx.daemon
        ctx.oauthService.apiSecret == 'top-secret'
        url.startsWith('https://login.microsoftonline.com/entra-tenant/adminconsent')
        url.contains('client_id=client-id')
    }

    void "sender in certificate mode builds ScribeJava service without a secret, per tenant"() {
        given:
        globals(true)
        TenantIdContext.setTenantId(3L)
        ConfigObject cfg = tenantCfg(useCertAuth: true, certificatePath: '/tmp/t3.pfx', certificatePassword: 'pw', certificateAlias: 'mailcert')
        resolver.resolve(3L) >> cfg

        when:
        TenantOAuthContext ctx = service.buildContext(cfg, 3L)
        String url = service.generateAuthCodeURL()

        then:
        ctx.authStrategy instanceof CertificateStrategy
        ctx.daemon
        ctx.oauthService.apiKey == 'client-id'
        ctx.oauthService.apiSecret == null
        url.startsWith('https://login.microsoftonline.com/entra-tenant/adminconsent')
    }

    void "tenants can mix credential types under the same global daemon flag"() {
        given:
        globals(true)

        when:
        TenantOAuthContext secretCtx = service.buildContext(tenantCfg(secret_val: 's'), 10L)
        TenantOAuthContext certCtx = service.buildContext(tenantCfg(useCertAuth: true, certificatePath: '/tmp/c.pfx'), 11L)

        then:
        secretCtx.authStrategy instanceof SecretKeyStrategy
        certCtx.authStrategy instanceof CertificateStrategy
        secretCtx.tenantId == 10L
        certCtx.tenantId == 11L
    }

    void "tenant daemon flag overrides the global one: #label"() {
        given:
        globals(globalDaemon)
        Map oAuth = [secret_val: 's']
        if (tenantDaemon != null) oAuth.daemon = tenantDaemon

        when:
        TenantOAuthContext ctx = service.buildContext(tenantCfg(oAuth), 20L)

        then:
        ctx.daemon == expected

        where:
        label                              | globalDaemon | tenantDaemon || expected
        'tenant true wins over global false' | false      | true         || true
        'tenant false wins over global true' | true       | false        || false
        'tenant unset falls back to global'  | true       | null         || true
        'tenant string value is parsed'      | false      | 'true'       || true
    }

    void "certificate tenant can be daemon while global daemon is false"() {
        given:
        globals(false)
        TenantIdContext.setTenantId(21L)
        ConfigObject cfg = tenantCfg(daemon: true, useCertAuth: true, certificatePath: '/tmp/t21.pfx')
        resolver.resolve(21L) >> cfg

        when:
        TenantOAuthContext ctx = service.buildContext(cfg, 21L)

        then:
        ctx.daemon
        ctx.authStrategy instanceof CertificateStrategy
        service.generateAuthCodeURL().startsWith('https://login.microsoftonline.com/entra-tenant/adminconsent')
    }

    void "sender rejects certificate auth when the tenant's effective daemon is false"() {
        given:
        globals(true)

        when:
        service.buildContext(tenantCfg(daemon: false, useCertAuth: true, certificatePath: '/tmp/c.pfx'), 22L)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('useCertAuth')
    }

    void "sender rejects certificate auth when daemon is false"() {
        given:
        globals(false)

        when:
        service.buildContext(tenantCfg(useCertAuth: true, certificatePath: '/tmp/c.pfx'), 4L)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('useCertAuth')
    }

    void "sender revoke with no stored token does not touch ScribeJava"() {
        given:
        globals(true)
        TenantIdContext.setTenantId(5L)
        resolver.resolve(5L) >> tenantCfg(secret_val: 's')

        when:
        String redirect = service.revokeToken()

        then:
        redirect == '/mailOAuth/index'
        service.tokenStore.getToken(5L) == null
    }

    void "reader builds ScribeJava service for #label"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', tenantId: 7L, clientId: 'client-id', graphTenantId: 'entra-tenant',
                scopes: SCOPE, callbackUrl: CALLBACK, emailAddress: 'box@example.com',
                secretId: secret, daemon: daemon, useCertAuth: cert, certificatePath: cert ? '/tmp/cert.pfx' : null)

        when:
        OAuth20Service scribe = store.buildOAuthService(config)
        String url = store.generateAuthCodeURLFor(config)

        then:
        scribe.apiKey == 'client-id'
        scribe.apiSecret == secret
        scribe.callback == CALLBACK
        url.startsWith(expectedUrlPrefix)

        where:
        label                  | secret       | daemon | cert  || expectedUrlPrefix
        'secret / interactive' | 'top-secret' | false  | false || 'https://login.microsoftonline.com/entra-tenant/oauth2/v2.0/authorize'
        'secret / daemon'      | 'top-secret' | true   | false || 'https://login.microsoftonline.com/entra-tenant/adminconsent'
        'certificate / daemon' | null         | true   | true  || 'https://login.microsoftonline.com/entra-tenant/adminconsent'
        'certificate only'     | null         | false  | true  || 'https://login.microsoftonline.com/entra-tenant/adminconsent'
    }

    void "reader with null callback omits redirect_uri from the ScribeJava auth URL"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', tenantId: 8L, clientId: 'client-id', graphTenantId: 'entra-tenant', scopes: 'scope', secretId: 's')

        expect:
        store.buildOAuthService(config).callback == null
        !store.generateAuthCodeURLFor(config).contains('redirect_uri')
    }

    void "reader daemon refresh without emailAddress fails with a clear error"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', tenantId: 9L, clientId: 'c', graphTenantId: 't', scopes: 's', secretId: 's', daemon: true)

        when:
        store.refreshTokenFor(config)

        then:
        def e = thrown(Exception)
        e.message.contains('emailAddress')
    }
}
