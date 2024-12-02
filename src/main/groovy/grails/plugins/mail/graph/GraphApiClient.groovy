package grails.plugins.mail.graph

import com.azure.core.credential.TokenCredential
import com.microsoft.graph.core.authentication.AzureIdentityAuthenticationProvider
import com.microsoft.graph.core.requests.GraphClientFactory
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.authentication.AuthenticationProvider
import grails.plugins.mail.debug.GraphDebugHandler
import grails.plugins.mail.graph.token.AdhocTokenCredential
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap


@Slf4j
@CompileStatic
class GraphApiClient {

    private GraphServiceClient graphServiceClient
    private TokenCredential tokenBasedAuthCredential
    private String scopes
    //To Avoid multiple graphClient Connections for same config as it does exhaust socket ports.
    private Map<String, GraphServiceClient> cache = new ConcurrentHashMap<String, GraphServiceClient>([:])

    GraphApiClient(TokenCredential tokenBasedAuthCredential, String scopes, Boolean debug) {
        this.tokenBasedAuthCredential = tokenBasedAuthCredential
        this.scopes = scopes
        AuthenticationProvider authenticationProvider = new AzureIdentityAuthenticationProvider(tokenBasedAuthCredential, new String[]{}, scopes.split(" "))
        OkHttpClient.Builder httpClientBuilder = GraphClientFactory.create(GraphServiceClient.graphClientOptions)
        if (debug) {
            httpClientBuilder = httpClientBuilder.addInterceptor(new GraphDebugHandler())
        }
        this.graphServiceClient = new GraphServiceClient(authenticationProvider, httpClientBuilder.build())
    }

    public GraphServiceClient getStandardMailClient() {
        return this.graphServiceClient
    }

    GraphServiceClient getClientFor(GraphConfig graphConfig, boolean reset = false) {
        if (!cache.get(graphConfig.configName) || reset) {
            AuthenticationProvider authenticationProvider = new AzureIdentityAuthenticationProvider(new AdhocTokenCredential(graphConfig: graphConfig), new String[]{}, graphConfig.scopes.split(" "))
            OkHttpClient.Builder httpClientBuilder = GraphClientFactory.create(GraphServiceClient.graphClientOptions)
            if (graphConfig.debug) {
                httpClientBuilder = httpClientBuilder.addInterceptor(new GraphDebugHandler(graphConfig.configName))
            }
            GraphServiceClient graphServiceClient = new GraphServiceClient(authenticationProvider, httpClientBuilder.build())
            cache.put(graphConfig.configName, graphServiceClient)
        }
        return cache.get(graphConfig.configName)
    }

    public clearCache() {
        cache.clear()
    }

}
