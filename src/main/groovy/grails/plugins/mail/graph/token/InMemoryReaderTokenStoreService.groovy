package grails.plugins.mail.graph.token

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import com.github.scribejava.core.revoke.TokenTypeHint
import grails.plugins.mail.oauth.MailOAuthUtil
import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.oauth.token.MemoryTokenStore
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class InMemoryReaderTokenStoreService implements ReaderTokenStoreService {

    private Map<String, GraphConfig> configStore = new ConcurrentHashMap([:])

    private Map<String, MemoryTokenStore> store = new ConcurrentHashMap([:])

    @Override
    OAuthToken getTokenFor(GraphConfig graphConfig) {
        OAuthToken oAuthToken = this.store.get(graphConfig.configName)?.getToken(graphConfig.tenantId)
        if (!oAuthToken) {
            if (graphConfig.daemon) {
                refreshTokenFor(graphConfig)
                return this.store.get(graphConfig.configName).getToken(graphConfig.tenantId)
            }
            log.error("No Access token generated for $graphConfig.configName. Please generate")
            return null
        }
        if (oAuthToken.expireAt > new Date()) {
            return oAuthToken
        }
        refreshTokenFor(graphConfig)
        return this.store.get(graphConfig.configName)?.getToken(graphConfig.getTenantId())
    }

    @Override
    void refreshTokenFor(GraphConfig graphConfig) {
        MemoryTokenStore tokenStore = this.store.get(graphConfig.configName)
        OAuth2AccessToken token
        if (graphConfig.daemon) {
            if (!graphConfig.emailAddress) {
                throw new Exception("Graph email has invalid config as daemon is true but emailAddress is not set")
            }
            token = getAuthService(graphConfig).getAccessTokenClientCredentialsGrant()
        } else {
            token = getAuthService(graphConfig).refreshAccessToken(tokenStore.getToken(graphConfig.tenantId).refreshToken)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(graphConfig.tenantId,authToken)
    }

    @Override
    String generateAuthCodeURLFor(GraphConfig graphConfig) {
        String state = UUID.randomUUID().toString().replaceAll('-', '')
        configStore.put(state, graphConfig)
        log.debug("Generated Auth URL for ${graphConfig.configName} with state ${state} ")
        if (graphConfig.daemon) {
            return MailOAuthUtil.buildAdminConsentUrl(state, graphConfig.graphTenantId, graphConfig.clientId, graphConfig.callbackUrl)
        }
        return getAuthService(graphConfig).getAuthorizationUrl(state)
    }

    @Override
    OAuthToken generateAccessTokenFor(GraphConfig graphConfig, String code, String state, Boolean forced = false) {
        if (!graphConfig)
            graphConfig = configStore.get(state)
        log.debug("Retrieved config from state via ${state} for ${graphConfig?.configName}")
        MemoryTokenStore tokenStore = new MemoryTokenStore()
        OAuth2AccessToken token
        if (graphConfig.daemon) {
            token = getAuthService(graphConfig).getAccessTokenClientCredentialsGrant()
        } else {
            token = getAuthService(graphConfig).getAccessToken(code)
            //TODO need to see what we can do for shared emailAddress validation in case of delegate flow.
            if (graphConfig.emailAddress && !graphConfig.isShared)
                MailOAuthUtil.validateToken(token.accessToken, graphConfig.emailAddress, getAuthService(graphConfig))
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(graphConfig.tenantId,authToken)
        this.store.put(graphConfig.configName, tokenStore)
        return authToken
    }

    @Override
    void revokeTokenFor(GraphConfig graphConfig) {
        MemoryTokenStore tokenStore = this.store.get(graphConfig.configName)
        OAuthToken oAuthToken = tokenStore.getToken(graphConfig.tenantId)
        if (!oAuthToken) {
            log.info("[GRAPH_EMAIL] [REVOKE_TOKEN] No token found for ${graphConfig.configName}")
            return
        }
        // clear locally stored token
        tokenStore.revokeToken(graphConfig.tenantId)

        OAuth20Service authService = getAuthService(graphConfig)

        // revoke refresh token first (stops future access)
        if (oAuthToken.refreshToken) {
            try {
                authService.revokeToken(oAuthToken.refreshToken, TokenTypeHint.REFRESH_TOKEN)
                log.debug("[GRAPH_READER_EMAIL] [REVOKE_TOKEN] Revoked refresh token")
            } catch (Exception e) {
                log.warn("[GRAPH_READER_EMAIL] [REVOKE_TOKEN] Failed to revoke refresh token due to : ${e.message}")
            }
        }

        // revoke access token (optional, expires in ~1h anyway)
        if (oAuthToken.accessToken) {
            try {
                authService.revokeToken(oAuthToken.accessToken, TokenTypeHint.ACCESS_TOKEN)
                log.debug("[GRAPH_READER_EMAIL] [REVOKE_TOKEN] Revoked access token")
            } catch (Exception e) {
                log.warn("[GRAPH_READER_EMAIL] [REVOKE_TOKEN] Failed to revoke access token due to : ${e.message}")
            }
        }
    }

    private OAuth20Service getAuthService(GraphConfig graphConfig) {
        new ServiceBuilder(graphConfig.clientId)
                .apiSecret(graphConfig.secretId).defaultScope(graphConfig.scopes)
                .callback(graphConfig.callbackUrl)
                .build(MicrosoftAzureActiveDirectory20Api.custom(graphConfig.graphTenantId))
    }
}
