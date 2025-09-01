package grails.plugins.mail.reader

import grails.config.Config
import grails.core.support.GrailsConfigurationAware

class ReaderTokenController implements GrailsConfigurationAware {

    def readerTokenStoreService

    private String redirectUri
    private boolean enabled

    @Override
    void setConfiguration(Config config) {
        this.redirectUri = config.getProperty('grails.mail.reader.graph.redirect_uri', String, '/emailConfig')
        this.enabled = config.getProperty('grails.mail.reader.enabled', Boolean) && config.getProperty('grails.mail.reader.graph.enabled', Boolean)
    }

    def callback(String code, String state, Boolean forced) {
        if (!enabled) {
            render status: 404, text: "Reader with Graph is not enabled for this environment"
            return
        }
        //Todo in actual implementation we would need to attach state with graphconfig so that callback can be received
        log.info("[GRAPH_EMAIL] [READER_OAUTH_CALLBACK] [RECEIVED] - CODE_PRESENT=${code != null}, STATE_PRESENT=${state != null} | forced = ${forced}")

        if ((!forced && !code) || !state) {
            log.warn("[GRAPH_EMAIL] [READER_OAUTH_CALLBACK] [INVALID_REQUEST] - Missing 'code' or 'state' | code=${code} | state=${state}")
            render status: 400, text: "Invalid request: Missing 'code' or 'state'."
            return
        }

        try {
            log.info("[GRAPH_EMAIL] [READER_OAUTH_CALLBACK] [PROCESSING] - Starting access token generation")

            // Ideally, validate state here (not implemented for now)
            readerTokenStoreService.generateAccessTokenFor(null, code, state, forced)

            log.info("[GRAPH_EMAIL] [READER_OAUTH_CALLBACK] [SUCCESS] - Access token generated and stored successfully")

            redirect(uri: redirectUri)
        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [READER_OAUTH_CALLBACK] [ERROR] - Failed to generate access token | code=${code} | state=${state}", ex)
            render status: 500, text: "Internal Server Error while generating access token"
        }
    }

}
