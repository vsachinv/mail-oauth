package grails.plugins.mail.oauth

import grails.util.Holders
import groovy.util.logging.Slf4j
import org.springframework.mail.MailAuthenticationException

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse

@Slf4j
class MailOAuthController {

    String uri = Holders.config.grails.mail.oAuth.redirect.uri
    def mailOAuthService

    def index() {
        log.debug("[GRAPH_EMAIL] [INDEX] - Accessed index endpoint")
    }

    def generate() {
        if (!grailsApplication.config.grails.mail.oAuth.enabled) {
            log.warn("[GRAPH_EMAIL] [GENERATE] - OAuth configuration is disabled")
            flash.warn = "Please enable mail OAuth configuration"
            redirect(uri: uri)
            return
        }

        log.debug("[GRAPH_EMAIL] [GENERATE] - Requested new AuthToken | Redirecting to Auth URL")
        redirect(url: mailOAuthService.generateAuthCodeURL())
    }

    def refresh() {
        log.info("[GRAPH_EMAIL] [REFRESH] - Requested refresh of AuthToken")
        mailOAuthService.refreshAccessToken(mailOAuthService.tokenStore.getToken())
        flash.message = "Refreshed Token"
        redirect(uri: uri)
    }

    def revoke() {
        log.debug("[GRAPH_EMAIL] [REVOKE] - Requested revoke of AuthToken")
        mailOAuthService.revokeToken()
        flash.message = "Token Revoked"
        redirect(uri: uri)
    }

    def callback(String code, String state) {
        if (!code) {
            log.warn("[GRAPH_EMAIL] [CALLBACK] - Missing code | Error=${params.error} | Description=${params.error_description}")
            flash.error = "Invalid code received error: ${params.error} \n Description: ${params.error_description}"
            redirect(uri: uri)
            return
        }

        log.debug("[GRAPH_EMAIL] [CALLBACK] - Received OAuth callback | Code=${code} | State=${state}")
        mailOAuthService.generateAccessToken(code, state)
        flash.message = "Successfully generated access token for $code"
        redirect(uri: uri)
    }

    def tokenStatus() {
        def token = mailOAuthService.tokenStore.getToken()

        if (!token) {
            log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - No token available")
            flash.error = "Access token is not available."
            redirect(uri: uri)
            return
        }

        if (token.expireAt < new Date()) {
            log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - Token expired at ${token.expireAt}")
            flash.warn = "Access token is invalid. Please generate using refresh token"
            redirect(uri: uri)
            return
        }

        log.debug("[GRAPH_EMAIL] [TOKEN_STATUS] - Token valid till ${token.expireAt}")
        flash.message = "Access token is valid till ${token.expireAt} UTC."
        redirect(uri: uri)
    }

    def sendTestMail(String email) {
        try {
            log.info("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Attempting to send test mail to ${email}")
            new InternetAddress(email).validate()

            sendMail {
                multipart false
                to email
                subject 'test email'
                body "test mail created at ${new Date()}"
            }

            log.info("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Successfully sent test mail to ${email}")
            flash.message = "Test mail sent to ${email}"

        } catch(AddressException addressException) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Invalid email address: ${email}", addressException)
            flash.error = "Test mail failed due to invalid email address. Please enter valid email address."

        } catch(OAuth2AccessTokenErrorResponse authException) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - OAuth2 Access Token error while sending mail", authException)
            flash.error = "Test mail failed with OAuth2 Access Token Errors. Please contact your Administrator."

        } catch (MailAuthenticationException mailAuthenticationException) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Authentication failed while sending mail", mailAuthenticationException)
            flash.error = "Authentication failed for configured email. Please contact your Administrator."

        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - General error while sending mail", ex)
            flash.error = "Test mail failed. Please contact your Administrator."
        }

        redirect(uri: uri)
    }
}

