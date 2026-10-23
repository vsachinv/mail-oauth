package grails.plugins.mail.oauth

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import com.github.scribejava.core.revoke.TokenTypeHint
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.plugins.mail.graph.auth.GraphAuthStrategy
import grails.plugins.mail.graph.auth.GraphAuthStrategyFactory
import grails.plugins.mail.oauth.token.OAuthToken
import grails.util.Holders
import groovy.util.logging.Slf4j


@Slf4j
class MailOAuthService implements GrailsConfigurationAware {

    def tokenStore
    def stateStoreService

    private String clientId
    private String apiScope
    private String callbackUrl
    private String tenantId
    private OAuth20Service oAuth20Service
    private GraphAuthStrategy authStrategy

    String generateAuthCodeURL() {
        String state = UUID.randomUUID().toString().replaceAll('-', '')
        stateStoreService.storeState(clientId, state)
        if (authStrategy.isDaemon()) {
            log.debug("[GRAPH_EMAIL] [GENERATE_AUTH_CODE_URL] Generating admin consent url")
            return MailOAuthUtil.buildAdminConsentUrl(state, tenantId, clientId, callbackUrl)
        }
        log.debug("[GRAPH_EMAIL] [GENERATE] - Requested new AuthToken | Redirecting to Auth URL")
        Map<String, String> additionalParams = [
                state: state,
                prompt: "login"
        ]
        return oAuth20Service.getAuthorizationUrl(additionalParams)
    }

    synchronized OAuthToken generateAccessToken(String code, String state, Boolean forced = false) {
        if (!forced && clientId != this.stateStoreService.getIdForState(state)) {
            throw new Exception('State mismatch. State sent is different from what received')
        }
        OAuth2AccessToken token
        if (authStrategy.isDaemon()) {
            token = authStrategy.acquireToken(oAuth20Service)
        } else {
            token = oAuth20Service.getAccessToken(code)
            MailOAuthUtil.validateToken(token.accessToken, Holders.config.getProperty('grails.mail.username'), oAuth20Service)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
        return authToken
    }

    synchronized OAuthToken refreshAccessToken(OAuthToken oldToken) {
        log.debug('[GRAPH_EMAIL] [REFRESH_ACCESS_TOKEN] Refreshing token')
        OAuth2AccessToken token
        if (authStrategy.isDaemon()) {
            token = authStrategy.acquireToken(oAuth20Service)
        } else {
            token = authStrategy.refreshToken(oAuth20Service, oldToken.refreshToken)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
        return authToken
    }

    OAuthToken getAccessToken() {
        OAuthToken oAuthToken = tokenStore.getToken()
        if (!oAuthToken) {
            if (authStrategy.isDaemon()) {
                return refreshAccessToken(null)
            }
            log.error('[GRAPH_EMAIL] [GET_ACCESS_TOKEN] No access token found. Generate one via /mailOAuth/generate')
            return null
        }
        if (oAuthToken.expireAt > new Date()) {
            return oAuthToken
        }
        return refreshAccessToken(oAuthToken)
    }

    void revokeToken() {
        OAuthToken oAuthToken = tokenStore.getToken()
        if (!oAuthToken) {
            log.info("[GRAPH_EMAIL] [REVOKE_TOKEN] No token found, nothing to revoke")
            return
        }

        // Always clear local store first
        tokenStore.revokeToken()

        // Try revoking refresh token (cuts off future access)
        if (oAuthToken.refreshToken) {
            try {
                oAuth20Service.revokeToken(oAuthToken.refreshToken, TokenTypeHint.REFRESH_TOKEN)
                log.debug("[GRAPH_EMAIL] [REVOKE_TOKEN] Revoked refresh token")
            } catch (Exception e) {
                log.warn("[GRAPH_EMAIL] [REVOKE_TOKEN] Failed to revoke refresh token due to : ${e.message}")
            }
        }

        // Try revoking access token (optional: expires within ~1 hour anyway)
        if (oAuthToken.accessToken) {
            try {
                oAuth20Service.revokeToken(oAuthToken.accessToken, TokenTypeHint.ACCESS_TOKEN)
                log.debug("[GRAPH_EMAIL] [REVOKE_TOKEN] Revoked access token")
            } catch (Exception e) {
                log.warn("[GRAPH_EMAIL] [REVOKE_TOKEN] Failed to revoke access token due to : ${e.message}")
            }
        }
    }

    @Override
    void setConfiguration(Config co) {
        this.clientId = co.getProperty('grails.mail.oAuth.client_id')
        this.apiScope = co.getProperty('grails.mail.oAuth.api_scope')
        this.callbackUrl = co.getProperty('grails.mail.oAuth.callback_url')
        this.tenantId = co.getProperty('grails.mail.oAuth.tenant_id')
        this.authStrategy = GraphAuthStrategyFactory.build(co)
        if (authStrategy.isDaemon() && !co.getProperty('grails.mail.username')) {
            throw new Exception("Invalid mail oauth configuration as Username is blank and daemon is true")
        }
        if (co.getProperty('grails.mail.oAuth.useCertAuth', Boolean, false) && !co.getProperty('grails.mail.oAuth.daemon', Boolean, false)) {
            throw new Exception("Invalid mail oauth configuration as useCertAuth is true but daemon is false. Certificate auth requires daemon mode")
        }
        // Always built: used by SecretKeyStrategy (daemon + secret), revokeToken and the auth-code flow.
        // ScribeJava rejects an empty apiSecret, so it is only set when configured (absent for certificate auth).
        String clientSecret = co.getProperty('grails.mail.oAuth.secret_val', String, null)
        ServiceBuilder builder = new ServiceBuilder(this.clientId)
        if (clientSecret) {
            builder.apiSecret(clientSecret)
        }
        this.oAuth20Service = builder.defaultScope(this.apiScope)
                .callback(this.callbackUrl)
                .build(MicrosoftAzureActiveDirectory20Api.custom(this.tenantId))
    }
}
