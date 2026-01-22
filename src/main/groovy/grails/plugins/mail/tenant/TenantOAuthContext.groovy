package grails.plugins.mail.tenant

import com.github.scribejava.core.oauth.OAuth20Service

class TenantOAuthContext {
    String tenantId
    OAuth20Service oauthService
    boolean daemon
    String redirectUri
    boolean enable
    String clientId
}
