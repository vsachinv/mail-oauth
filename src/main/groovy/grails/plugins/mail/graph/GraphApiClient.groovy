package grails.plugins.mail.graph

import com.azure.core.credential.TokenCredential
import com.microsoft.graph.serviceclient.GraphServiceClient
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
        this.graphServiceClient = new GraphServiceClient(tokenBasedAuthCredential, scopes)
    }

    GraphServiceClient getStandardMailClient() {
        return this.graphServiceClient
    }

    GraphServiceClient getClientFor(GraphConfig graphConfig) {
        if (!cache.get(graphConfig.configName)) {
            this.graphServiceClient = new GraphServiceClient(new AdhocTokenCredential(graphConfig: graphConfig), graphConfig.scopes)
            cache.put(graphConfig.configName, this.graphServiceClient)
        }
        return cache.get(graphConfig.configName)
    }

    public clearCache() {
        cache.clear()
    }

}
