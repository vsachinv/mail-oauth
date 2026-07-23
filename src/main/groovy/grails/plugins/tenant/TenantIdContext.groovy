package grails.plugins.tenant;

import grails.plugins.mail.oauth.MailOAuthUtil;
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j;
import org.slf4j.MDC;

@Slf4j
@CompileStatic
public class TenantIdContext {
    private static final ThreadLocal<Long> contextHolder = new ThreadLocal<>()

    static void setTenantId(Long tenantId) {
        if (tenantId != null) {
            MDC.put(MailOAuthUtil.TENANT_ID_LOG_VAR_NAME, tenantId.toString())
        } else {
            MDC.remove(MailOAuthUtil.TENANT_ID_LOG_VAR_NAME)
        }
        contextHolder.set(tenantId)
    }

    static Long getTenantId() {
        return contextHolder.get()
    }

    static void clear() {
        contextHolder.remove()
        MDC.remove(MailOAuthUtil.TENANT_ID_LOG_VAR_NAME)
    }

}
