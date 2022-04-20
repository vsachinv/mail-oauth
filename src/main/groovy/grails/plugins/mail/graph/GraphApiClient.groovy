package grails.plugins.mail.graph


import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.requests.GraphServiceClient
import grails.plugins.mail.graph.token.TokenBasedAuthCredential
import groovy.util.logging.Slf4j


@Slf4j
class GraphApiClient {

    GraphServiceClient graphServiceClient
    private TokenBasedAuthCredential tokenBasedAuthCredential
    private String scopes

    GraphApiClient(TokenBasedAuthCredential tokenBasedAuthCredential, String scopes) {
        this.tokenBasedAuthCredential = tokenBasedAuthCredential
        this.scopes = scopes
        TokenCredentialAuthProvider tokenCredAuthProvider = new TokenCredentialAuthProvider(scopes.split(' ').toList(), tokenBasedAuthCredential)
        this.graphServiceClient = GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredAuthProvider)
                .buildClient()
    }

}
