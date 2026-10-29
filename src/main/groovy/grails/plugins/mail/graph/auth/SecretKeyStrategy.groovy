package grails.plugins.mail.graph.auth

import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class SecretKeyStrategy implements GraphAuthStrategy {

    private final boolean daemon

    SecretKeyStrategy(boolean daemon) {
        this.daemon = daemon
    }

    @Override
    OAuth2AccessToken acquireToken(OAuth20Service service) throws Exception {
        log.debug('[GRAPH_AUTH] [SECRET_KEY] Acquiring token via client credentials grant')
        return service.getAccessTokenClientCredentialsGrant()
    }

    @Override
    OAuth2AccessToken refreshToken(OAuth20Service service, String refreshToken) throws Exception {
        log.debug('[GRAPH_AUTH] [SECRET_KEY] Refreshing token via refresh token grant')
        return service.refreshAccessToken(refreshToken)
    }

    @Override
    boolean isDaemon() {
        return daemon
    }
}
