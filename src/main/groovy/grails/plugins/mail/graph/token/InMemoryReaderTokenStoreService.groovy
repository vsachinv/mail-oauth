package grails.plugins.mail.graph.token

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
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
        OAuthToken oAuthToken = this.store.get(graphConfig.configName)?.token
        if (!oAuthToken) {
            if (graphConfig.daemon) {
                refreshTokenFor(graphConfig)
                return this.store.get(graphConfig.configName).token
            }
            log.error("No Access token generated for $graphConfig.configName. Please generate")
            return null
        }
        if (oAuthToken.expireAt > new Date()) {
            return oAuthToken
        }
        refreshTokenFor(graphConfig)
        return this.store.get(graphConfig.configName)?.token
    }

    @Override
    void refreshTokenFor(GraphConfig graphConfig) {
        MemoryTokenStore tokenStore = this.store.get(graphConfig.configName)
        OAuth2AccessToken token
        if (graphConfig.daemon) {
            token = getAuthService(graphConfig).getAccessTokenClientCredentialsGrant()
        } else {
            token = getAuthService(graphConfig).refreshAccessToken(tokenStore.token.refreshToken)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
    }

    @Override
    String generateAuthCodeURLFor(GraphConfig graphConfig) {
        String state = UUID.randomUUID().toString().replaceAll('-', '')
        configStore.put(state, graphConfig)
        log.debug("Generated Auth URL for ${graphConfig.configName} with state ${state} ")
        return getAuthService(graphConfig).getAuthorizationUrl(state)
    }

    @Override
    OAuthToken generateAccessTokenFor(GraphConfig graphConfig, String code, String state) {
        graphConfig = configStore.get(state)
        log.debug("Retrieved config from state via ${state} for ${graphConfig?.configName}")
        MemoryTokenStore tokenStore = new MemoryTokenStore()
        OAuth2AccessToken token = getAuthService(graphConfig).getAccessToken(code)
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(authToken)
        this.store.put(graphConfig.configName, tokenStore)
        return authToken
    }

    @Override
    void revokeTokenFor(GraphConfig graphConfig) {
        MemoryTokenStore tokenStore = this.store.get(graphConfig.configName)
        tokenStore.revokeToken()
    }

    private OAuth20Service getAuthService(GraphConfig graphConfig) {
        new ServiceBuilder(graphConfig.clientId)
                .apiSecret(graphConfig.secretId).defaultScope(graphConfig.scopes)
                .callback(graphConfig.callbackUrl)
                .build(MicrosoftAzureActiveDirectory20Api.custom(graphConfig.tenantId))
    }
}
