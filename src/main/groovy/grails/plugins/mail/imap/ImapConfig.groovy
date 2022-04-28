package grails.plugins.mail.imap

import grails.plugins.mail.graph.GraphConfig
import groovy.transform.CompileStatic

@CompileStatic
class ImapConfig {
    String configName
    String username
    String password
    String host
    String protocol
    int port
    Map otherProperties
    boolean oAuthEnabled = false
    GraphConfig graphConfig
}
