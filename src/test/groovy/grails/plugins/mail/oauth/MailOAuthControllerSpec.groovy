package grails.plugins.mail.oauth

import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.oauth2.OAuth2Error
import grails.plugins.mail.oauth.token.OAuthToken
import grails.plugins.mail.oauth.token.TokenStore
import grails.plugins.tenant.TenantContextProvider
import grails.plugins.tenant.TenantIdContext
import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.mail.MailAuthenticationException
import spock.lang.Specification

/**
 * MailOAuthController drives the consent flow for the *current* tenant (from tenantContextProvider).
 * It delegates every decision to MailOAuthService / TenantMailService, so these tests check the
 * delegation, the tenant id handed down, the flash messages and the redirects.
 */
class MailOAuthControllerSpec extends Specification implements ControllerUnitTest<MailOAuthController> {

    private static final Long TENANT = 7L
    private static final String INDEX = '/mailOAuth/index'

    MailOAuthService mailOAuthService
    TenantMailService tenantMailService
    TokenStore tokenStore

    def setup() {
        // MailOAuthUtil.redirectUri() reads the global config through Holders
        config.merge([grails: [mail: [oAuth: [enabled: true, daemon: false, 'redirect.uri': INDEX]]]])

        mailOAuthService = Mock(MailOAuthService)
        tenantMailService = Mock(TenantMailService)
        tokenStore = Mock(TokenStore)
        controller.mailOAuthService = mailOAuthService
        controller.tenantMailService = tenantMailService
        controller.tenantContextProvider = Stub(TenantContextProvider) { getCurrentTenantId() >> TENANT }
        _ * mailOAuthService.getTokenStore() >> tokenStore
    }

    def cleanup() {
        TenantIdContext.clear()
    }

    private static OAuthToken tokenExpiringAt(Date date) {
        new OAuthToken(accessToken: 'abc', expireAt: date)
    }

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000

    private static Date inOneDay() { new Date(System.currentTimeMillis() + ONE_DAY_MS) }

    private static Date oneDayAgo() { new Date(System.currentTimeMillis() - ONE_DAY_MS) }

    void "index renders successfully"() {
        when:
        controller.index()

        then:
        response.status == 200
    }

    void "generate redirects to whatever the service returns and clears the tenant afterwards"() {
        given:
        Long tenantSeenByService = null

        when:
        controller.generate()

        then:
        1 * mailOAuthService.generateAuthCodeURL() >> { tenantSeenByService = TenantIdContext.getTenantId(); 'https://auth.example.com' }
        response.redirectedUrl == 'https://auth.example.com'
        tenantSeenByService == TENANT
        TenantIdContext.getTenantId() == null
    }

    void "generate redirects back to index when the service declines (OAuth disabled for the tenant)"() {
        when:
        controller.generate()

        then:
        1 * mailOAuthService.generateAuthCodeURL() >> INDEX
        response.redirectedUrl == INDEX
    }

    void "refresh refreshes the current tenant's token and redirects"() {
        given:
        OAuthToken existing = tokenExpiringAt(new Date())
        ConfigObject cfg = new ConfigObject()
        cfg.username = 'mailbox@example.com'

        when:
        controller.refresh()

        then:
        1 * mailOAuthService.getTenantConfig(TENANT) >> cfg
        1 * tokenStore.getToken(TENANT) >> existing
        1 * mailOAuthService.refreshAccessToken(TENANT, existing) >> tokenExpiringAt(inOneDay())
        flash.message == 'Refreshed Token'
        response.redirectedUrl == INDEX
        TenantIdContext.getTenantId() == null
    }

    void "revoke revokes the current tenant's token and redirects"() {
        when:
        controller.revoke()

        then:
        1 * mailOAuthService.revokeToken() >> INDEX
        flash.message == 'Token Revoked'
        response.redirectedUrl == INDEX
        TenantIdContext.getTenantId() == null
    }

    void "callback passes code, state, params and flags to the service and redirects where it says"() {
        given:
        params.error = 'invalid_request'

        when:
        controller.callback('authCode', 'state123', false, false)

        then:
        1 * mailOAuthService.generateAccessToken('authCode', 'state123', { it.error == 'invalid_request' }, false, false) >> INDEX
        flash.message == 'Successfully generated access token'
        response.redirectedUrl == INDEX
    }

    void "callback with a missing code is still delegated to the service, which decides the redirect"() {
        when:
        controller.callback(null, null, false, false)

        then:
        1 * mailOAuthService.generateAccessToken(null, null, _, false, false) >> INDEX
        response.redirectedUrl == INDEX
    }

    void "callback reports a failure in flash and still redirects to index"() {
        when:
        controller.callback('authCode', 'state123', false, false)

        then:
        1 * mailOAuthService.generateAccessToken(*_) >> { throw new RuntimeException('boom') }
        flash.error == 'Failed to generate token due to : boom'
        response.redirectedUrl == INDEX
    }

    void "tokenStatus warns when no token is stored for the tenant"() {
        when:
        controller.tokenStatus()

        then:
        1 * tokenStore.getToken(TENANT) >> null
        flash.error == 'Access token is not available.'
        response.redirectedUrl == INDEX
    }

    void "tokenStatus warns when the token is expired"() {
        when:
        controller.tokenStatus()

        then:
        1 * tokenStore.getToken(TENANT) >> tokenExpiringAt(oneDayAgo())
        flash.warn == 'Access token is invalid. Please generate using refresh token'
        response.redirectedUrl == INDEX
    }

    void "tokenStatus reports a valid token"() {
        when:
        controller.tokenStatus()

        then:
        1 * tokenStore.getToken(TENANT) >> tokenExpiringAt(inOneDay())
        flash.message.startsWith('Access token is valid till')
        response.redirectedUrl == INDEX
    }

    void "sendTestMail sends through TenantMailService for a valid address"() {
        when:
        controller.sendTestMail('test@example.com')

        then:
        1 * tenantMailService.sendMail(_) >> Mock(org.springframework.mail.MailMessage)
        flash.message == 'Test mail sent to test@example.com'
        !flash.warn
        response.redirectedUrl == INDEX
    }

    void "sendTestMail reports disabled when the tenant's mail is switched off"() {
        when:
        controller.sendTestMail('test@example.com')

        then:
        1 * tenantMailService.sendMail(_) >> null
        flash.warn == 'Mail sending is disabled by configuration for this tenant. Test mail was not sent.'
        !flash.message
        !flash.error
        response.redirectedUrl == INDEX
    }

    void "sendTestMail rejects an invalid address before sending"() {
        when:
        controller.sendTestMail('invalid-email')

        then:
        0 * tenantMailService.sendMail(_)
        flash.error.contains('invalid email address')
        response.redirectedUrl == INDEX
    }

    void "sendTestMail maps an OAuth2 token error to a specific message"() {
        given:
        Response raw = new Response(400, 'Bad Request', [:], '{"error":"invalid_grant"}')

        when:
        controller.sendTestMail('test@example.com')

        then:
        1 * tenantMailService.sendMail(_) >> { throw new OAuth2AccessTokenErrorResponse(OAuth2Error.INVALID_GRANT, 'expired', null, raw) }
        flash.error.contains('OAuth2 Access Token Errors')
        response.redirectedUrl == INDEX
    }

    void "sendTestMail maps a mail authentication failure to a specific message"() {
        when:
        controller.sendTestMail('test@example.com')

        then:
        1 * tenantMailService.sendMail(_) >> { throw new MailAuthenticationException('auth failed') }
        flash.error.contains('Authentication failed')
        response.redirectedUrl == INDEX
    }

    void "sendTestMail maps any other failure to the generic message"() {
        when:
        controller.sendTestMail('test@example.com')

        then:
        1 * tenantMailService.sendMail(_) >> { throw new IllegalStateException('nope') }
        flash.error == 'Test mail failed. Please contact your Administrator.'
        response.redirectedUrl == INDEX
    }
}
