package cl.bunnycure.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AppAsyncConfigTest {

    private final AppAsyncConfig config = new AppAsyncConfig();

    @Test
    void getAsyncExecutor_ReturnsConfiguredThreadPool() {
        Executor executor = config.getAsyncExecutor();
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);

        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, pool.getCorePoolSize());
        assertEquals(12, pool.getMaxPoolSize());
        assertEquals(100, pool.getQueueCapacity());
        assertEquals("bunnycure-async-", pool.getThreadNamePrefix());

        pool.shutdown();
    }

    @Test
    void getAsyncUncaughtExceptionHandler_LogsWithoutThrowing() throws NoSuchMethodException {
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        assertNotNull(handler);

        Method method = AppAsyncConfigTest.class.getDeclaredMethod("dummyMethod");
        assertDoesNotThrow(() ->
                handler.handleUncaughtException(
                        new RuntimeException("Simulated async error"),
                        method,
                        "param1", 123
                )
        );
    }

    void dummyMethod() {}
}
