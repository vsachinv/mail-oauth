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
    }

    def generate() {
        if (!grailsApplication.config.grails.mail.oAuth.enabled) {
            flash.warn = "Please enable mail OAuth configuration"
            redirect(uri: uri)
            return
        }
        log.debug("Requested for new AuthToken")
        redirect(url: mailOAuthService.generateAuthCodeURL())
    }

    def refresh() {
        log.debug("Requested for refresh AuthToken")
        mailOAuthService.refreshAccessToken(mailOAuthService.tokenStore.getToken())
        flash.message = "Refreshed Token"
        redirect(uri: uri)
    }

    def revoke() {
        log.debug("Requested for revoke AuthToken")
        mailOAuthService.revokeToken()
        flash.message = "Token Revoked"
        redirect(uri: uri)
    }

    def callback(String code, String state) {
        if (!code) {
            flash.error = "Invalid code received error: ${params.error} \n Description: ${params.error_description}"
            redirect(uri: uri)
            return
        }
        mailOAuthService.generateAccessToken(code, state)
        flash.message = "Successfully generated access token for $code"
        redirect(uri: uri)
    }

    def tokenStatus() {
        if (!mailOAuthService.tokenStore.getToken()) {
            flash.error = "Access token is not available."
            redirect(uri: uri)
            return
        }
        if (mailOAuthService.tokenStore.getToken().expireAt < new Date()) {
            flash.warn = "Access token is invalid. Please generate using refresh token"
            redirect(uri: uri)
            return
        }
        flash.message = "Access token is valid till ${mailOAuthService.tokenStore.getToken().expireAt} UTC."
        redirect(uri: uri)
    }

    def sendTestMail(String email) {
        try {
            new InternetAddress(email).validate()
            sendMail {
                multipart false
                to email
                subject 'test email'
                body "test mail created at ${new Date()}"
            }
            flash.message = "Test mail sent to ${email}"
        } catch(AddressException addressException) {
            log.error("Invalid email address : ${email} reveived. Test mail failed with error: ", addressException)
            flash.error = "Test mail failed due to invalid email address. Please enter valid email address."
        } catch(OAuth2AccessTokenErrorResponse authException) {
            log.error("Test mail failed with OAuth2 access token error: ", authException)
            flash.error = "Test mail failed with OAuth2 Access Token Errors. Please contact your Administrator."
        } catch (MailAuthenticationException mailAuthenticationException) {
            log.error('Exception while sending test email due to', mailAuthenticationException)
            flash.error = "Authentication failed for configured email. Please contact your Administrator."
        } catch (Exception ex) {
            log.error('Exception while sending test email due to', ex)
            flash.error = "Test mail failed. Please contact your Administrator."
        }
        redirect(uri: uri)
    }

}
