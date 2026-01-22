package grails.plugins.mail.tenant

import groovy.util.logging.Slf4j

import java.util.concurrent.*

@Slf4j
class TenantMailExecutorRegistry {

    private final ConcurrentMap<Long, ExecutorService> executors =
            new ConcurrentHashMap<>()

    ExecutorService executorFor(Long tenantId, Integer poolSize) {

        executors.computeIfAbsent(tenantId) {
            log.info("[MAIL] Creating executor for tenant={}", tenantId)

            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    poolSize ?: 5,
                    poolSize ?: 5,
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
            executor.allowCoreThreadTimeOut(true)
            return executor
        }
    }

    void shutdownAll() {
        executors.values().each {
            it.shutdown()
        }
    }
}
