package grails.plugins.mail.tenant

import com.github.scribejava.core.oauth.OAuth20Service
import grails.plugins.mail.graph.auth.GraphAuthStrategy

class TenantOAuthContext {
    Long tenantId
    OAuth20Service oauthService
    boolean daemon
    boolean enable
    String clientId
    GraphAuthStrategy authStrategy
}
