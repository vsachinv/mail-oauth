package grails.plugins.mail.graph.token

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.core.credential.TokenRequestContext
import grails.plugins.mail.oauth.MailOAuthService
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.util.logging.Slf4j
import reactor.core.publisher.Mono

import java.time.ZoneOffset

@Slf4j
class TokenBasedAuthCredential implements TokenCredential {
    MailOAuthService mailOAuthService

    @Override
    Mono<AccessToken> getToken(TokenRequestContext request) {
        OAuthToken oAuthToken = mailOAuthService.accessToken
        return Mono.just(new AccessToken(oAuthToken.accessToken, oAuthToken.expireAt.toInstant().atOffset(ZoneOffset.UTC)))
    }
}
