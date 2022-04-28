package grails.plugins.mail.graph.token

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.transform.CompileStatic
import reactor.core.publisher.Mono

import java.time.ZoneOffset

@CompileStatic
class AdhocTokenCredential implements TokenCredential {

    OAuthToken oAuthToken

    @Override
    Mono<AccessToken> getToken(TokenRequestContext request) {
        return Mono.just(new AccessToken(oAuthToken.accessToken, oAuthToken.expireAt.toInstant().atOffset(ZoneOffset.UTC)))
    }
}