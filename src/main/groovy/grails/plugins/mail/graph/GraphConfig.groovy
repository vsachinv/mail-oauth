package grails.plugins.mail.graph

import groovy.transform.CompileStatic

@CompileStatic
class GraphConfig {
    String configName //Should be unique and mandatory
    String tenantId
    String clientId
    String secretId
    String scopes
    String callbackUrl
}
