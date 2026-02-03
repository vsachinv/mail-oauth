package grails.plugins.mail.tenant

import com.github.scribejava.core.oauth.OAuth20Service

class TenantOAuthContext {
    Long tenantId
    OAuth20Service oauthService
    boolean daemon
    String redirectUri
    boolean enable
    String clientId
}
