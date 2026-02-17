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
import java.util.concurrent.TimeUnit


@Slf4j
@CompileStatic
class GraphApiClient {

    private GraphServiceClient graphServiceClient
    private TokenCredential tokenBasedAuthCredential
    private String scopes
    private Long connectTimeout // In seconds
    private Long writeTimeout // In seconds
    private Long readTimeout // In seconds

    //To Avoid multiple graphClient Connections for same config as it does exhaust socket ports.
    private Map<String, GraphServiceClient> cache = new ConcurrentHashMap<String, GraphServiceClient>([:])

    GraphApiClient(TokenCredential tokenBasedAuthCredential, String scopes, Boolean debug, Long connectTimeout, Long writeTimeout, Long readTimeout) {
        this.tokenBasedAuthCredential = tokenBasedAuthCredential
        this.scopes = scopes
        this.connectTimeout = connectTimeout
        this.writeTimeout = writeTimeout
        this.readTimeout = readTimeout
        AuthenticationProvider authenticationProvider = new AzureIdentityAuthenticationProvider(tokenBasedAuthCredential, new String[]{}, scopes.split(" "))
        OkHttpClient.Builder httpClientBuilder = GraphClientFactory.create(GraphServiceClient.graphClientOptions)
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)   // connection timeout
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)    // write timeout per chunk
                .readTimeout(readTimeout, TimeUnit.SECONDS)    // read timeout per chunk

        if (debug) {
            httpClientBuilder = httpClientBuilder.addInterceptor(new GraphDebugHandler())
        }
        this.graphServiceClient = new GraphServiceClient(authenticationProvider, httpClientBuilder.build())
    }

    GraphServiceClient getStandardMailClient() {
        return this.graphServiceClient
    }

    GraphServiceClient getClientFor(GraphConfig graphConfig, boolean reset = false) {
        String key = "${graphConfig.tenantId}-${graphConfig.configName}"
        if (!cache.get(key) || reset) {
            AuthenticationProvider authenticationProvider = new AzureIdentityAuthenticationProvider(new AdhocTokenCredential(graphConfig: graphConfig), new String[]{}, graphConfig.scopes.split(" "))
            OkHttpClient.Builder httpClientBuilder = GraphClientFactory.create(GraphServiceClient.graphClientOptions)
                    .connectTimeout(this.connectTimeout, TimeUnit.SECONDS)   // connection timeout
                    .writeTimeout(this.writeTimeout, TimeUnit.SECONDS)    // write timeout per chunk
                    .readTimeout(this.readTimeout, TimeUnit.SECONDS)     // read timeout per chunk

            if (graphConfig.debug) {
                httpClientBuilder = httpClientBuilder.addInterceptor(new GraphDebugHandler(key))
            }
            GraphServiceClient graphServiceClient = new GraphServiceClient(authenticationProvider, httpClientBuilder.build())
            cache.put(key, graphServiceClient)
        }
        return cache.get(key)
    }

    void clearCache() {
        cache.clear()
    }

}
