package grails.plugins.mail.oauth.token

import groovy.transform.CompileStatic

@CompileStatic
interface TokenStore {

    void saveToken(OAuthToken token);

    OAuthToken getToken();

    void revokeToken();

}