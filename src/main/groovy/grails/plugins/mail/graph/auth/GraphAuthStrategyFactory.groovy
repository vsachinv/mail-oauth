package grails.plugins.mail.graph.auth

import grails.config.Config
import grails.plugins.mail.graph.GraphConfig
import groovy.transform.CompileStatic

@CompileStatic
class GraphAuthStrategyFactory {

    static GraphAuthStrategy build(Config config) {
        boolean useCertAuth = config.getProperty('grails.mail.oAuth.useCertAuth', Boolean, false)
        if (useCertAuth) {
            return new CertificateStrategy(
                    config.getProperty('grails.mail.oAuth.client_id'),
                    config.getProperty('grails.mail.oAuth.tenant_id'),
                    config.getProperty('grails.mail.oAuth.certificatePath'),
                    config.getProperty('grails.mail.oAuth.certificateBase64', String, null),
                    config.getProperty('grails.mail.oAuth.certificatePassword', String, null),
                    config.getProperty('grails.mail.oAuth.certificateAlias', String, null),
                    config.getProperty('grails.mail.oAuth.api_scope')
            )
        }
        return new SecretKeyStrategy(config.getProperty('grails.mail.oAuth.daemon', Boolean, false))
    }

    static GraphAuthStrategy build(GraphConfig graphConfig) {
        if (graphConfig.useCertAuth) {
            return new CertificateStrategy(
                    graphConfig.clientId,
                    graphConfig.tenantId,
                    graphConfig.certificatePath,
                    graphConfig.certificateBase64,
                    graphConfig.certificatePassword,
                    graphConfig.certificateAlias,
                    graphConfig.scopes
            )
        }
        return new SecretKeyStrategy(graphConfig.daemon)
    }
}