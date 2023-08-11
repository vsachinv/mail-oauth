package grails.plugins.mail.oauth.token

import groovy.transform.CompileStatic

@CompileStatic
class MemoryTokenStore implements TokenStore {

    private OAuthToken token

    @Override
    void saveToken(OAuthToken token) {
        this.token = token
    }

    @Override
    OAuthToken getToken() {
        return this.token
    }

    @Override
    void revokeToken() {
        this.token = null
    }
}
