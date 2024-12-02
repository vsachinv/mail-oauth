package grails.plugins.mail.graph

import groovy.transform.CompileStatic

@CompileStatic
class GraphConfig implements Serializable {

    private static final long serialVersionUID = 1952358793540268673L

    String configName //Should be unique and mandatory
    String tenantId
    String clientId
    String secretId
    String scopes
    String callbackUrl
    boolean daemon = false
    boolean debug = false
}
