package dfm.medischeduler_routeservice;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration for the Route Service.
 *
 * Configures a thread pool for {@code @Async} annotated methods and
 * installs a custom exception handler that logs uncaught exceptions
 * from asynchronous tasks.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Provides a bounded thread pool for async task execution.
     * Core/max pool size is set to 2 to limit concurrency while still
     * allowing overlap between Redis writes and API calls.
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncExceptionHandler();
    }

    /**
     * Logs uncaught exceptions thrown by {@code @Async} methods, including
     * the method name and parameter values for debugging.
     */
    static class SimpleAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable e, Method method, Object... params) {
            System.err.println("Async exception in method: " + method.getName());
            System.err.println("Exception: " + e.getMessage());
            for (Object param : params) {
                System.err.println("Param: " + param);
            }
        }
    }
}
