package grails.plugins.mail.reader

import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import groovy.util.logging.Slf4j

@Slf4j
class ReaderTokenController implements GrailsConfigurationAware {

    def readerTokenStoreService
    private String redirectUri

    @Override
    void setConfiguration(Config config) {
        this.redirectUri = config.getProperty('grails.mail.reader.graph.redirect_uri', String, '/emailConfig')
    }

    /**
     * Handles the OAuth callback to generate and store the access token.
     * @param code - The authorization code returned by the OAuth provider.
     * @param state - The state parameter used for CSRF protection.
     */
    def callback() {
        String code = params.code
        String state = params.state

        log.info("[GRAPH_EMAIL] [OAUTH_CALLBACK] [RECEIVED] - CODE_PRESENT=${code != null}, STATE_PRESENT=${state != null}")

        if (!code || !state) {
            log.warn("[GRAPH_EMAIL] [OAUTH_CALLBACK] [INVALID_REQUEST] - Missing 'code' or 'state' | code=${code} | state=${state}")
            render status: 400, text: "Invalid request: Missing 'code' or 'state'."
            return
        }

        try {
            log.info("[GRAPH_EMAIL] [OAUTH_CALLBACK] [PROCESSING] - Starting access token generation")

            // Ideally, validate state here (not implemented for now)
            readerTokenStoreService.generateAccessTokenFor(null, code, state)

            log.info("[GRAPH_EMAIL] [OAUTH_CALLBACK] [SUCCESS] - Access token generated and stored successfully")

            redirect(uri: redirectUri)
        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [OAUTH_CALLBACK] [ERROR] - Failed to generate access token | code=${code} | state=${state}", ex)
            render status: 500, text: "Internal Server Error while generating access token"
        }
    }
}