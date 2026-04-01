package grails.plugins.mail.tenant

import grails.plugins.tenant.TenantIdContext

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class TenantAwareExecutorService implements  ExecutorService{

    private final ExecutorService delegate

    TenantAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate
    }

    private Runnable wrap(Runnable task) {
        Long tenantId = TenantIdContext.getTenantId()
        return {
            try {
                TenantIdContext.setTenantId(tenantId)
                task.run()
            } finally {
                TenantIdContext.clear()
            }
        } as Runnable
    }

    @Override
    void execute(Runnable command) {
        delegate.execute(wrap(command))
    }
    // delegate remaining methods
    @Override void shutdown() { delegate.shutdown() }
    @Override List<Runnable> shutdownNow() { delegate.shutdownNow() }
    @Override boolean isShutdown() { delegate.isShutdown() }
    @Override boolean isTerminated() { delegate.isTerminated() }
    @Override boolean awaitTermination(long timeout, TimeUnit unit) { delegate.awaitTermination(timeout, unit) }
    @Override <T> Future<T> submit(Callable<T> task) { delegate.submit(task) }
    @Override <T> Future<T> submit(Runnable task, T result) { delegate.submit(wrap(task), result) }
    @Override Future<?> submit(Runnable task) { delegate.submit(wrap(task)) }
    @Override <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) { delegate.invokeAll(tasks) }
    @Override <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { delegate.invokeAll(tasks, timeout, unit) }
    @Override <T> T invokeAny(Collection<? extends Callable<T>> tasks) { delegate.invokeAny(tasks) }
    @Override <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { delegate.invokeAny(tasks, timeout, unit) }
}
