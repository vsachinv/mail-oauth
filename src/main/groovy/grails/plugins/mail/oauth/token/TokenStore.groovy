package grails.plugins.mail.oauth.token

import groovy.transform.CompileStatic

@CompileStatic
interface TokenStore {

    void saveToken(Long tenantId,OAuthToken token);

    OAuthToken getToken(Long tenantId);

    void revokeToken(Long tenantId);

}