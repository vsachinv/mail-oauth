package grails.plugins.mail.graph

import com.azure.core.credential.TokenCredential
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.requests.GraphServiceClient
import grails.plugins.mail.graph.token.AdhocTokenCredential
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


@Slf4j
@CompileStatic
class GraphApiClient {

    private GraphServiceClient graphServiceClient
    private TokenCredential tokenBasedAuthCredential
    private String scopes

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


    public GraphServiceClient getClientFor(AdhocTokenCredential tokenCredential, String scopes) {
        TokenCredentialAuthProvider tokenCredAuthProvider = new TokenCredentialAuthProvider(scopes.split(' ').toList(), tokenCredential)
        return GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredAuthProvider)
                .buildClient()
    }

}
