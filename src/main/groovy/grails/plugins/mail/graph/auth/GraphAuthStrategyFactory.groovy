package grails.plugins.mail.graph.auth

import grails.plugins.mail.graph.GraphConfig
import groovy.transform.CompileStatic

@CompileStatic
class GraphAuthStrategyFactory {

    /**
     * Sender: build from a resolved per-tenant mail config (the {@code grails.mail} / {@code tenant_<id>.grails.mail}
     * block). {@code daemon} is the effective daemon flag for this tenant.
     */
    static GraphAuthStrategy build(ConfigObject mailCfg, boolean daemon) {
        Map oAuth = (mailCfg?.get('oAuth') instanceof Map) ? (Map) mailCfg.get('oAuth') : [:]
        boolean useCertAuth = Boolean.parseBoolean(String.valueOf(oAuth.get('useCertAuth') ?: false))
        if (useCertAuth) {
            if (!daemon) {
                throw new IllegalStateException('Invalid mail oauth configuration: useCertAuth is true but daemon is false. Certificate auth requires daemon mode')
            }
            return new CertificateStrategy(
                    str(oAuth.get('client_id')),
                    str(oAuth.get('tenant_id')),
                    str(oAuth.get('certificatePath')),
                    str(oAuth.get('certificatePassword')),
                    str(oAuth.get('certificateAlias')),
                    str(oAuth.get('api_scope'))
            )
        }
        return new SecretKeyStrategy(daemon)
    }

    /** Reader: build from a GraphConfig supplied by the consuming application. */
    static GraphAuthStrategy build(GraphConfig graphConfig) {
        if (graphConfig.useCertAuth) {
            return new CertificateStrategy(
                    graphConfig.clientId,
                    graphConfig.graphTenantId,
                    graphConfig.certificatePath,
                    graphConfig.certificatePassword,
                    graphConfig.certificateAlias,
                    graphConfig.scopes
            )
        }
        return new SecretKeyStrategy(graphConfig.daemon)
    }

    private static String str(Object v) {
        (v == null || v instanceof ConfigObject && ((ConfigObject) v).isEmpty()) ? null : String.valueOf(v)
    }
}
