package grails.plugins.mail.graph.token

import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.transform.CompileStatic

@CompileStatic
interface ReaderTokenStoreService {

    OAuthToken getTokenFor(GraphConfig graphConfig)

    void refreshTokenFor(GraphConfig graphConfig)

    String generateAuthCodeURLFor(GraphConfig graphConfig)

    OAuthToken generateAccessTokenFor(GraphConfig graphConfig, String code, String state)

    void revokeTokenFor(GraphConfig graphConfig)

}