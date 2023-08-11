package grails.plugins.mail.graph.token

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.oauth.token.OAuthToken
import grails.util.Holders
import groovy.transform.CompileStatic
import reactor.core.publisher.Mono

import java.time.ZoneOffset

@CompileStatic
class AdhocTokenCredential implements TokenCredential {

    GraphConfig graphConfig
    //Need to access ReaderToken for getting Token
    private ReaderTokenStoreService readerTokenStoreService = (Holders.applicationContext.getBean('readerTokenStoreService') as ReaderTokenStoreService)

    @Override
    Mono<AccessToken> getToken(TokenRequestContext request) {
        OAuthToken oAuthToken = readerTokenStoreService.getTokenFor(graphConfig)
        return Mono.just(new AccessToken(oAuthToken.accessToken, oAuthToken.expireAt.toInstant().atOffset(ZoneOffset.UTC)))
    }
}