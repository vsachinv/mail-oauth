package grails.plugins.mail.oauth


import groovy.util.logging.Slf4j
import org.springframework.mail.MailAuthenticationException

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse

@Slf4j
class MailOAuthController {

    MailOAuthService mailOAuthService
    TenantMailService tenantMailService

    def index() {
        log.debug("[GRAPH_EMAIL] [INDEX] - Accessed index endpoint")
    }

    def generate(Long tenantId) {
        log.debug("[GRAPH_EMAIL] [GENERATE] - Requested new AuthToken | Redirecting to Auth URL for tenantId {}",tenantId)
        redirect(url: mailOAuthService.generateAuthCodeURL(tenantId))
    }

    def refresh(Long tenantId) {
        log.info("[GRAPH_EMAIL] [REFRESH] - Requested refresh of AuthToken for tenantId {}",tenantId)
        ConfigObject cfg = mailOAuthService.getTenantConfig(tenantId)
        if(!cfg){
            log.warn("Tenant configuration is not available for TenantId {}",tenantId)
            redirect(uri: MailOAuthUtil.REDIRECT_URI)
        }
        mailOAuthService.refreshAccessToken(tenantId,mailOAuthService.tokenStore.getToken(tenantId))
        flash.message = "Refreshed Token"
        redirect(uri: MailOAuthUtil.REDIRECT_URI)
    }

    def revoke(Long tenantId) {
        log.info("[GRAPH_EMAIL] [REVOKE] - Requested revoke of AuthToken for tenantId {}",tenantId)
        flash.message = "Token Revoked"
        redirect(uri: mailOAuthService.revokeToken(tenantId))
    }

    def callback(String code, String state,Boolean admin_consent, Boolean forced) {
        log.debug("[GRAPH_EMAIL] [CALLBACK] - Received OAuth callback | Code=${code} | State=${state} | forced=${forced} | admin_consent=${admin_consent}")
        String redirectUri
        try{
            redirectUri = mailOAuthService.generateAccessToken(code, state,params,admin_consent,forced)
            flash.message = "Successfully generated access token"
        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [CALLBACK] [FAILED] - Received OAuth callback | Code=${code} | State=${state} ", ex)
            flash.error = "Failed to generate token due to : " + ex.message
        }
        redirect(uri: redirectUri)
    }

    def tokenStatus(Long tenantId) {
        def cfg = mailOAuthService.getTenantConfig(tenantId)
        def token = mailOAuthService.tokenStore.getToken(tenantId)
        if (!token) {
            log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - No token available")
            flash.error = "Access token is not available."
            redirect(uri: MailOAuthUtil.REDIRECT_URI)
            return
        }
        if (token.expireAt < new Date()) {
            log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - Token expired at ${token.expireAt}")
            flash.warn = "Access token is invalid. Please generate using refresh token"
            redirect(uri: MailOAuthUtil.REDIRECT_URI)
            return
        }
        log.debug("[GRAPH_EMAIL] [TOKEN_STATUS] - Token valid till ${token.expireAt}")
        flash.message = "Access token is valid till ${token.expireAt} UTC."
        redirect(uri: MailOAuthUtil.REDIRECT_URI)
    }

    def sendTestMail(Long tenantId, String email) {
        log.info("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Attempting to send test mail to ${email} and tenantId ${tenantId}")
        def cfg =  mailOAuthService.getTenantConfig(tenantId)
        try {
            new InternetAddress(email).validate()
            tenantMailService.sendMail(tenantId) {
                multipart false
                to email
                subject 'test email'
                body "test mail created at ${new Date()}"
            }
            flash.message = "Test mail sent to ${email}"
        } catch(AddressException addressException) {
            log.error("GRAPH_EMAIL] [SEND_TEST_MAIL] - Invalid email address : ${email} reveived. Test mail failed with error: ", addressException)
            flash.error = "Test mail failed due to invalid email address. Please enter valid email address."
        } catch(OAuth2AccessTokenErrorResponse authException) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - OAuth2 Access Token error while sending test mail", authException)
            flash.error = "Test mail failed with OAuth2 Access Token Errors. Please contact your Administrator."
        } catch (MailAuthenticationException mailAuthenticationException) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Authentication failed while sending test mail", mailAuthenticationException)
            flash.error = "Authentication failed for configured email. Please contact your Administrator."
        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [SEND_TEST_MAIL] - General error while sending test mail", ex)
            flash.error = "Test mail failed. Please contact your Administrator."
        }
        redirect(uri: MailOAuthUtil.REDIRECT_URI)
    }

}
