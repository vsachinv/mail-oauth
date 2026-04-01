package grails.plugins.mail.tenant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.DisposableBean

import java.util.concurrent.*

@Slf4j
@CompileStatic
class TenantMailExecutorRegistry implements  DisposableBean {

    private final ConcurrentMap<Long, ExecutorService> executors =
            new ConcurrentHashMap<>()
    private static final Integer DEFAULT_POOL_SIZE = 5
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30

    ExecutorService executorFor(Long tenantId, Integer poolSize) {

        executors.computeIfAbsent(tenantId) {
            log.info("[MAIL] Creating executor for tenant={}", tenantId)

            ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                    poolSize ?: DEFAULT_POOL_SIZE,
                    poolSize ?: DEFAULT_POOL_SIZE,
                    60,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new ThreadFactory() {
                        @Override
                        Thread newThread(Runnable r) {
                            new Thread(r, "tenant-mail-${tenantId}")
                        }
                    }
            )
            delegate.allowCoreThreadTimeOut(true)
            // Wrap executor before returning
            return new TenantAwareExecutorService(delegate)
        }
    }

    void shutdownAll() {
        log.info("[MAIL] Shutting down all tenant mail executors ({} tenants)", executors.size())
        executors.each { Long tenantId, ExecutorService executor ->
            try {
                executor.shutdown()
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("[MAIL] Executor for tenant={} did not terminate within {}s, forcing shutdown",
                            tenantId, SHUTDOWN_TIMEOUT_SECONDS)
                    executor.shutdownNow()
                }
            } catch (InterruptedException e) {
                log.warn("[MAIL] Interrupted while shutting down executor for tenant={}", tenantId)
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        executors.clear()
    }

    @Override
    void destroy() throws Exception {
        shutdownAll()
    }
}
