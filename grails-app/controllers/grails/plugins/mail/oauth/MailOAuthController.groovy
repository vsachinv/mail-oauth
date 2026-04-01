package grails.plugins.mail.oauth

import grails.plugins.tenant.TenantContextProvider
import grails.plugins.tenant.TenantIdContext
import groovy.util.logging.Slf4j
import org.springframework.mail.MailAuthenticationException

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse

@Slf4j
class MailOAuthController {

    MailOAuthService mailOAuthService
    TenantMailService tenantMailService
    TenantContextProvider tenantContextProvider

    def index() {
        log.debug("[GRAPH_EMAIL] [INDEX] - Accessed index endpoint")
    }

    def generate() {
        try {
            log.debug("[GRAPH_EMAIL] [GENERATE] - Requested new AuthToken | Redirecting to Auth URL")
            Long tenantId = tenantContextProvider.getCurrentTenantId()
            TenantIdContext.setTenantId(tenantId)
            redirect(url: mailOAuthService.generateAuthCodeURL())
        }finally {
            TenantIdContext.clear()
        }
    }

    def refresh() {
        try {
            Long tenantId = tenantContextProvider.getCurrentTenantId()
            TenantIdContext.setTenantId(tenantId)
            log.info("[GRAPH_EMAIL] [REFRESH] - Requested refresh of AuthToken for tenantId {}", tenantId)
            ConfigObject cfg = mailOAuthService.getTenantConfig(tenantId)
            if (!cfg) {
                log.warn("Tenant configuration is not available for TenantId {}", tenantId)
                redirect(uri: MailOAuthUtil.redirectUri())
            }
            mailOAuthService.refreshAccessToken(tenantId, mailOAuthService.tokenStore.getToken(tenantId))
            flash.message = "Refreshed Token"
            redirect(uri: MailOAuthUtil.redirectUri())
        }finally {
            TenantIdContext.clear()
        }
    }

    def revoke() {
        try {
            log.info("[GRAPH_EMAIL] [REVOKE] - Requested revoke of AuthToken")
            Long tenantId = tenantContextProvider.getCurrentTenantId()
            TenantIdContext.setTenantId(tenantId)
            flash.message = "Token Revoked"
            redirect(uri: mailOAuthService.revokeToken())
        }finally {
            TenantIdContext.clear()
        }
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

    def tokenStatus() {
        try {
            Long tenantId = tenantContextProvider.getCurrentTenantId()
            TenantIdContext.setTenantId(tenantId)
            def token = mailOAuthService.tokenStore.getToken(tenantId)
            if (!token) {
                log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - No token available")
                flash.error = "Access token is not available."
                redirect(uri: MailOAuthUtil.redirectUri())
                return
            }
            if (token.expireAt < new Date()) {
                log.warn("[GRAPH_EMAIL] [TOKEN_STATUS] - Token expired at ${token.expireAt}")
                flash.warn = "Access token is invalid. Please generate using refresh token"
                redirect(uri: MailOAuthUtil.redirectUri())
                return
            }
            log.debug("[GRAPH_EMAIL] [TOKEN_STATUS] - Token valid till ${token.expireAt}")
            flash.message = "Access token is valid till ${token.expireAt} UTC."
            redirect(uri: MailOAuthUtil.redirectUri())
        }finally {
            TenantIdContext.clear()
        }
    }

    def sendTestMail(String email) {
        log.info("[GRAPH_EMAIL] [SEND_TEST_MAIL] - Attempting to send test mail to ${email}")
        try {
            new InternetAddress(email).validate()
            tenantMailService.sendMail {
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
        redirect(uri: MailOAuthUtil.redirectUri())
    }

}
