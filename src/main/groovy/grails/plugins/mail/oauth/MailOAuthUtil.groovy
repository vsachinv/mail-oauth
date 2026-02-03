package grails.plugins.mail.oauth

import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import java.nio.charset.StandardCharsets

@CompileStatic
class MailOAuthUtil {

    private static final String GRAPH_ME_URL = "https://graph.microsoft.com/v1.0/me"
    static final String TENANT_PREFIX = "tenant_"
    static final Integer MAX_ATTACHMENT_SIZE_IN_MB = 3

    // TODO need to find solution to handle using API rather hard coded string GRAPH_ME_URL.
    @CompileDynamic
    public static void validateToken(String accessToken, String username, OAuth20Service oAuth20Service) {
        OAuthRequest request = new OAuthRequest(Verb.GET, GRAPH_ME_URL);
        oAuth20Service.signRequest(accessToken, request)
        Response response = oAuth20Service.execute(request)
        if (response.getCode() != 200) {
            throw new RuntimeException("Failed to fetch user profile to validate: " + response.getBody())
        }
        def json = new JsonSlurper().parseText(response.body)
        String email = json.mail ?: json.userPrincipalName
        if (email != username) {
            throw new RuntimeException("Generated token is not for account ${username} rather but for ${email}")
        }
    }

    public static String buildAdminConsentUrl(String state, String tenantId, String clientId) {
        return String.format("https://login.microsoftonline.com/%s/adminconsent" + "?client_id=%s" + "&state=%s",
                tenantId,
                urlEncode(clientId),
                urlEncode(state))
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String buildOAuthState(Long tenantId, String state) {
        String raw = "tenantId=${tenantId}|state=\"${state}\""
        return URLEncoder.encode(raw, StandardCharsets.UTF_8)
    }

    static Map<String, String> parseState(String stateParam) {
        if (!stateParam) {
            return [:]
        }
        // If URL-encoded, decode first
        String decoded = URLDecoder.decode(stateParam, StandardCharsets.UTF_8)
        Map<String, String> result = [:]
        decoded.split("\\|").each { part ->
            String[] kv = part.split("=", 2)
            if (kv.length == 2) {
                String key = kv[0]
                String value = kv[1].replaceAll('^"|"$', '')
                result.put(key, value);
            }
        }
        return result
    }

}
