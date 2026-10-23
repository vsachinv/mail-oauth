package grails.plugins.mail.graph.auth

import com.github.scribejava.core.oauth.OAuth20Service
import grails.config.Config
import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.token.InMemoryReaderTokenStoreService
import grails.plugins.mail.oauth.MailOAuthService
import grails.plugins.mail.oauth.token.MemoryTokenStore
import org.grails.config.PropertySourcesConfig
import spock.lang.Specification

/**
 * Exercises real ScribeJava OAuth20Service construction (no network) for every auth mode:
 * secret/interactive, secret/daemon and certificate/daemon, for both the sender and the reader.
 */
class AuthModeConfigurationSpec extends Specification {

    private static final String CALLBACK = 'https://app.example.com/mailOAuth/callback'

    private static Config senderConfig(Map overrides = [:]) {
        Map base = [
                'grails.mail.username'            : 'mailbox@example.com',
                'grails.mail.oAuth.client_id'     : 'client-id',
                'grails.mail.oAuth.tenant_id'     : 'tenant-id',
                'grails.mail.oAuth.api_scope'     : 'https://graph.microsoft.com/.default',
                'grails.mail.oAuth.callback_url'  : CALLBACK,
                'grails.mail.oAuth.daemon'        : false,
                'grails.mail.oAuth.useCertAuth'   : false,
        ]
        new PropertySourcesConfig(base + overrides)
    }

    private static MailOAuthService senderService(Map overrides = [:]) {
        MailOAuthService service = new MailOAuthService()
        service.tokenStore = new MemoryTokenStore()
        service.stateStoreService = [storeState: { String id, String state -> }, getIdForState: { String s -> 'client-id' }]
        service.setConfiguration(senderConfig(overrides))
        return service
    }

    private static OAuth20Service scribeService(MailOAuthService service) {
        service.@oAuth20Service as OAuth20Service
    }

    void "sender builds ScribeJava service with secret in interactive mode and produces auth-code URL"() {
        when:
        MailOAuthService service = senderService(['grails.mail.oAuth.secret_val': 'top-secret'])
        OAuth20Service scribe = scribeService(service)
        String url = service.generateAuthCodeURL()

        then:
        scribe
        scribe.apiKey == 'client-id'
        scribe.apiSecret == 'top-secret'
        scribe.callback == CALLBACK
        url.startsWith('https://login.microsoftonline.com/tenant-id/oauth2/v2.0/authorize')
        url.contains('client_id=client-id')
        url.contains('prompt=login')
        url.contains('redirect_uri=')
    }

    void "sender in secret daemon mode still builds ScribeJava service and returns admin consent URL"() {
        when:
        MailOAuthService service = senderService(['grails.mail.oAuth.secret_val': 'top-secret', 'grails.mail.oAuth.daemon': true])
        String url = service.generateAuthCodeURL()

        then:
        scribeService(service).apiSecret == 'top-secret'
        url.startsWith('https://login.microsoftonline.com/tenant-id/adminconsent')
        url.contains('client_id=client-id')
    }

    void "sender in certificate mode builds ScribeJava service without a secret"() {
        when:
        MailOAuthService service = senderService([
                'grails.mail.oAuth.daemon'             : true,
                'grails.mail.oAuth.useCertAuth'        : true,
                'grails.mail.oAuth.certificatePath'    : '/tmp/cert.pfx',
                'grails.mail.oAuth.certificatePassword': 'pw',
        ])
        OAuth20Service scribe = scribeService(service)

        then:
        scribe
        scribe.apiKey == 'client-id'
        scribe.apiSecret == null
        service.@authStrategy instanceof CertificateStrategy
        service.generateAuthCodeURL().startsWith('https://login.microsoftonline.com/tenant-id/adminconsent')
    }

    void "sender rejects certificate auth when daemon is false"() {
        when:
        senderService(['grails.mail.oAuth.useCertAuth': true, 'grails.mail.oAuth.certificatePath': '/tmp/cert.pfx'])

        then:
        def e = thrown(Exception)
        e.message.contains('useCertAuth')
    }

    void "sender rejects daemon mode without username"() {
        when:
        senderService(['grails.mail.oAuth.secret_val': 's', 'grails.mail.oAuth.daemon': true, 'grails.mail.username': ''])

        then:
        def e = thrown(Exception)
        e.message.contains('Username')
    }

    void "sender revoke with no stored token does not touch ScribeJava"() {
        given:
        MailOAuthService service = senderService(['grails.mail.oAuth.secret_val': 's'])

        when:
        service.revokeToken()

        then:
        noExceptionThrown()
        service.tokenStore.getToken() == null
    }

    void "reader builds ScribeJava service for #label"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', clientId: 'client-id', tenantId: 'tenant-id',
                scopes: 'https://graph.microsoft.com/.default', callbackUrl: CALLBACK, emailAddress: 'box@example.com',
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
        'secret / interactive' | 'top-secret' | false  | false || 'https://login.microsoftonline.com/tenant-id/oauth2/v2.0/authorize'
        'secret / daemon'      | 'top-secret' | true   | false || 'https://login.microsoftonline.com/tenant-id/adminconsent'
        'certificate / daemon' | null         | true   | true  || 'https://login.microsoftonline.com/tenant-id/adminconsent'
        'certificate only'     | null         | false  | true  || 'https://login.microsoftonline.com/tenant-id/adminconsent'
    }

    void "reader with null callback omits redirect_uri from the ScribeJava auth URL"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', clientId: 'client-id', tenantId: 'tenant-id',
                scopes: 'scope', secretId: 's')

        expect:
        store.buildOAuthService(config).callback == null
        !store.generateAuthCodeURLFor(config).contains('redirect_uri')
    }

    void "reader daemon refresh without emailAddress fails with a clear error"() {
        given:
        InMemoryReaderTokenStoreService store = new InMemoryReaderTokenStoreService()
        GraphConfig config = new GraphConfig(configName: 'cfg', clientId: 'c', tenantId: 't', scopes: 's', secretId: 's', daemon: true)

        when:
        store.refreshTokenFor(config)

        then:
        def e = thrown(Exception)
        e.message.contains('emailAddress')
    }
}
