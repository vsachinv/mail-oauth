package grails.plugins.mail.graph.auth

import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service

interface GraphAuthStrategy {

    /**
     * Acquire a new token using client-credentials grant.
     * SecretKeyStrategy delegates to the configured OAuth20Service.
     * CertificateStrategy signs a JWT assertion and posts directly to the token endpoint.
     */
    OAuth2AccessToken acquireToken(OAuth20Service service) throws Exception

    /**
     * Refresh an existing token using the refresh-token grant (interactive flows only).
     * CertificateStrategy throws UnsupportedOperationException.
     */
    OAuth2AccessToken refreshToken(OAuth20Service service, String refreshToken) throws Exception

    /**
     * Returns true when this strategy only supports client-credentials (no interactive flow).
     */
    boolean isDaemon()
}
