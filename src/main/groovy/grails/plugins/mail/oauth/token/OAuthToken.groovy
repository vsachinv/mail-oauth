package grails.plugins.mail.oauth.token

import com.github.scribejava.core.model.OAuth2AccessToken
import groovy.time.TimeCategory
import groovy.transform.CompileStatic
import groovy.transform.ToString

@ToString
class OAuthToken implements Serializable {

    String accessToken
    String refreshToken
    Integer expiresIn
    Date expireAt

    OAuthToken(){
    }

    OAuthToken(OAuth2AccessToken token) {
        this.accessToken = token.accessToken
        this.refreshToken = token.refreshToken
        this.expiresIn = token.expiresIn
        use(TimeCategory) {
            expireAt = new Date() + (this.expiresIn).seconds
        }
    }
}
