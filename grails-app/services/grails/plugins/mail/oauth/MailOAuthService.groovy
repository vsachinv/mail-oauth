package grails.plugins.mail.oauth

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.util.logging.Slf4j


@Slf4j
class MailOAuthService implements GrailsConfigurationAware {

    def tokenStore
    def stateStoreService

    private String clientId
    private String clientSecret
    private String apiScope
    private String callbackUrl
    private String tenantId
    private OAuth20Service oAuth20Service

    private boolean daemon = false

    String generateAuthCodeURL() {
        String state = UUID.randomUUID().toString().replaceAll('-', '')
        stateStoreService.storeState(clientId, state)
        return oAuth20Service.getAuthorizationUrl(state)
    }

    synchronized OAuthToken generateAccessToken(String code, String state) {
        if (clientId != this.stateStoreService.getIdForState(state)) {
            throw new Exception('State mismatch. State sent is different from what received')
        }
        OAuth2AccessToken token = oAuth20Service.getAccessToken(code)
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
        return authToken
    }

    synchronized OAuthToken refreshAccessToken(OAuthToken oldToken) {
        log.debug('Refreshing token')
        OAuth2AccessToken token
        if(daemon){
            token = oAuth20Service.getAccessTokenClientCredentialsGrant()
        } else {
            token = oAuth20Service.refreshAccessToken(oldToken.refreshToken)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
        return authToken
    }

    OAuthToken getAccessToken() {
        OAuthToken oAuthToken = tokenStore.getToken()
        if (!oAuthToken) {
            if(daemon){
                return refreshAccessToken(null)
            }
            log.error("No Access token generated for mail send. Please generate using /mailOAuth/generate uri")
            return null
        }
        if (oAuthToken.expireAt > new Date()) {
            return oAuthToken
        }
        oAuthToken = refreshAccessToken(oAuthToken)
        return oAuthToken
    }

    void revokeToken() {
        tokenStore.revokeToken()
    }

    @Override
    void setConfiguration(Config co) {
        this.clientId = co.getProperty('grails.mail.oAuth.client_id')
        this.clientSecret = co.getProperty('grails.mail.oAuth.secret_val')
        this.apiScope = co.getProperty('grails.mail.oAuth.api_scope')
        this.callbackUrl = co.getProperty('grails.mail.oAuth.callback_url')
        this.tenantId = co.getProperty('grails.mail.oAuth.tenant_id')
        this.daemon = co.getProperty('grails.mail.oAuth.daemon', Boolean, false)
        this.oAuth20Service = new ServiceBuilder(this.clientId)
                .apiSecret(this.clientSecret).defaultScope(this.apiScope)
                .callback(this.callbackUrl)
                .build(MicrosoftAzureActiveDirectory20Api.custom(this.tenantId))
    }
}
