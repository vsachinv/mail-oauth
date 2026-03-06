package grails.plugins.mail.oauth

import com.github.scribejava.apis.MicrosoftAzureActiveDirectory20Api
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import com.github.scribejava.core.revoke.TokenTypeHint
import grails.plugins.mail.graph.sender.SessionStateStoreService
import grails.plugins.mail.oauth.token.OAuthToken
import grails.plugins.mail.oauth.token.TokenStore
import grails.plugins.tenant.TenantContextProvider
import grails.plugins.mail.tenant.TenantOAuthContext
import grails.plugins.tenant.TenantIdContext
import groovy.util.logging.Slf4j


@Slf4j
class MailOAuthService  {

    TokenStore tokenStore
    TenantMailConfigResolverService tenantMailConfigResolverService
    SessionStateStoreService stateStoreService
    TenantContextProvider tenantContextProvider

    String generateAuthCodeURL() {
        Long tenantId = TenantIdContext.getTenantId()
        log.info("Inside generateAuthCodeURL for tenantId {}",tenantId)
        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)
        if(!cfg){
            log.warn("Tenant configuration is not available for TenantId {}",tenantId)
            return MailOAuthUtil.redirectUri()
        }
        TenantOAuthContext ctx = buildContext(cfg,tenantId)
        if (!ctx.enable) {
            log.warn("[GRAPH_EMAIL] [GENERATE] - OAuth configuration is disabled")
            flash.warn = "Please enable mail OAuth configuration"
            return MailOAuthUtil.redirectUri()
        }
        String state = UUID.randomUUID().toString().replaceAll('-', '')
        String stateTenantId = MailOAuthUtil.buildOAuthState(tenantId,state)
        log.info("stateTenantId = {}",stateTenantId)
        stateStoreService.storeState(ctx.clientId, stateTenantId)
        if (ctx.daemon) {
            log.debug("[GRAPH_EMAIL] [GENERATE_AUTH_CODE_URL] Generating admin consent url")
            return MailOAuthUtil.buildAdminConsentUrl(stateTenantId, cfg.oAuth.tenant_id, ctx.clientId,  MailOAuthUtil.callBackUrl())
        }
        log.debug("[GRAPH_EMAIL] [GENERATE] - Requested new AuthToken | Redirecting to Auth URL")
        Map<String, String> additionalParams = [
                state: stateTenantId,
                prompt: "login"
        ]
        return ctx.oauthService.getAuthorizationUrl(additionalParams)
    }

    synchronized String generateAccessToken(String code, String stateTenantId,Map params, Boolean admin_consent, Boolean forced = false)  throws Exception{
        Map stateMap = MailOAuthUtil.parseState(stateTenantId)
        Long tenantId = stateMap.tenantId as Long
        String state = stateTenantId
        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)
        if(!cfg){
            log.warn("Tenant configuration is not available for TenantId {}",tenantId)
            return MailOAuthUtil.redirectUri()
        }
        TenantOAuthContext ctx = buildContext(cfg,tenantId)
        if (!(ctx.daemon && (code || admin_consent)) && !code && !forced) {
            log.warn("[GRAPH_EMAIL] [CALLBACK] -[conditions:${ctx.daemon}, ${code}, ${admin_consent}, ${forced} ]-  Missing code | Error=${params.error} | Description=${params.error_description}")
            flash.error = "Invalid code received error: ${params.error} \n Description: ${params.error_description}"
            return MailOAuthUtil.redirectUri()
        }
        if (!forced && ctx.clientId != this.stateStoreService.getIdForState(state)) {
            throw new Exception('State mismatch. State sent is different from what received')
        }
        OAuth2AccessToken token
        if (ctx.daemon) {
            token = ctx.oauthService.getAccessTokenClientCredentialsGrant()
        } else {
            token = ctx.oauthService.getAccessToken(code)
            MailOAuthUtil.validateToken(token.accessToken, cfg.username, ctx.oauthService)
        }
        OAuthToken authToken = new OAuthToken(token)
        tokenStore.saveToken(tenantId, authToken)
        return MailOAuthUtil.redirectUri()
    }


    OAuthToken getAccessToken() {
        Long tenantId = TenantIdContext.getTenantId()
        log.info('Get token for tenantId  {}',tenantId)
        OAuthToken token = tokenStore.getToken(tenantId)
        if (!token || token.expireAt.before(new Date())) {
            token = refreshAccessToken(tenantId,token)
        }
        token
    }

    synchronized OAuthToken refreshAccessToken(Long tenantId, OAuthToken oldToken) {
        log.info('Refreshing token for tenantId  {}',tenantId)
        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)
        TenantOAuthContext ctx = buildContext(cfg,tenantId)
        OAuth2AccessToken oauthToken = ctx.daemon ?
                ctx.oauthService.getAccessTokenClientCredentialsGrant() :
                ctx.oauthService.refreshAccessToken(oldToken.refreshToken)

        OAuthToken newToken = new OAuthToken(oauthToken)
        tokenStore.saveToken(tenantId, newToken)
        return newToken
    }

    String revokeToken() {
        Long tenantId = TenantIdContext.getTenantId()
        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)
        if(!cfg){
            log.warn("Tenant configuration is not available for TenantId {}",tenantId)
            return MailOAuthUtil.redirectUri()
        }
        OAuthToken oAuthToken = tokenStore.getToken(tenantId)
        TenantOAuthContext ctx = buildContext(cfg,tenantId)
        if (!oAuthToken) {
            log.info("[GRAPH_EMAIL] [REVOKE_TOKEN] No token found, nothing to revoke")
            return MailOAuthUtil.redirectUri()
        }

        // Always clear local store first
        tokenStore.revokeToken(tenantId)

        // Try revoking refresh token (cuts off future access)
        if (oAuthToken.refreshToken) {
            try {
                ctx.oauthService.revokeToken(oAuthToken.refreshToken, TokenTypeHint.REFRESH_TOKEN)
                log.debug("[GRAPH_EMAIL] [REVOKE_TOKEN] Revoked refresh token")
            } catch (Exception e) {
                log.warn("[GRAPH_EMAIL] [REVOKE_TOKEN] Failed to revoke refresh token due to : ${e.message}")
            }
        }

        // Try revoking access token (optional: expires within ~1 hour anyway)
        if (oAuthToken.accessToken) {
            try {
                ctx.oauthService.revokeToken(oAuthToken.accessToken, TokenTypeHint.ACCESS_TOKEN)
                log.debug("[GRAPH_EMAIL] [REVOKE_TOKEN] Revoked access token")
            } catch (Exception e) {
                log.warn("[GRAPH_EMAIL] [REVOKE_TOKEN] Failed to revoke access token due to : ${e.message}")
            }
        }
        return MailOAuthUtil.redirectUri()
    }


    TenantOAuthContext buildContext(ConfigObject cfg, Long tenantId) {
        OAuth20Service service =
                new ServiceBuilder(cfg.oAuth.client_id as String)
                        .apiSecret(cfg.oAuth.secret_val as String)
                        .defaultScope(MailOAuthUtil.apiScope())
                        .callback(MailOAuthUtil.callBackUrl())
                        .build(MicrosoftAzureActiveDirectory20Api.custom(cfg.oAuth.tenant_id as String))

        new TenantOAuthContext(
                tenantId: tenantId,
                oauthService: service,
                daemon: MailOAuthUtil.isDaemon(),
                enable: cfg.oAuth.enabled ?: false,
                clientId: cfg.oAuth.client_id
        )
    }

    ConfigObject getTenantConfig(Long tenantId){
       return tenantMailConfigResolverService.resolve(tenantId)
    }

}
