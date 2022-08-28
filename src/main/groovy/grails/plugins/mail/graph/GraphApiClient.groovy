package grails.plugins.mail.graph

import com.azure.core.credential.TokenCredential
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.requests.GraphServiceClient
import grails.plugins.mail.graph.token.AdhocTokenCredential
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.util.concurrent.ConcurrentHashMap


@Slf4j
@CompileStatic
class GraphApiClient {

    private GraphServiceClient graphServiceClient
    private TokenCredential tokenBasedAuthCredential
    private String scopes
    //To Avoid multiple graphClient Connections for same config as it does exhaust socket ports.
    private Map<String, GraphServiceClient> cache = new ConcurrentHashMap<String, GraphServiceClient>([:])

    GraphApiClient(TokenCredential tokenBasedAuthCredential, String scopes) {
        this.tokenBasedAuthCredential = tokenBasedAuthCredential
        this.scopes = scopes
        TokenCredentialAuthProvider tokenCredAuthProvider = new TokenCredentialAuthProvider(scopes.split(' ').toList(), tokenBasedAuthCredential)
        this.graphServiceClient = GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredAuthProvider)
                .buildClient()
    }

    public GraphServiceClient getStandardMailClient() {
        return this.graphServiceClient
    }

    public GraphServiceClient getClientFor(GraphConfig graphConfig) {
        if (!cache.get(graphConfig.configName)) {
            TokenCredentialAuthProvider tokenCredAuthProvider = new TokenCredentialAuthProvider(graphConfig.scopes.split(' ').toList(), new AdhocTokenCredential(graphConfig: graphConfig))
            cache.put(graphConfig.configName, GraphServiceClient
                    .builder()
                    .authenticationProvider(tokenCredAuthProvider)
                    .buildClient())
        }
        return cache.get(graphConfig.configName)
    }

    public clearCache() {
        cache.clear()
    }

}
