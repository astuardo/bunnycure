package cl.bunnycure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuración centralizada de ejecución asíncrona (@EnableAsync).
 * 
 * Previene inanición de memoria y creación descontrolada de hilos nativos
 * mediante un ThreadPoolTaskExecutor con capacidad de cola acotada y
 * política de saturación CallerRunsPolicy.
 */
@Slf4j
@Configuration
@EnableAsync
public class AppAsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bunnycure-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("[ASYNC-ERROR] Excepción no capturada en método asíncrono '{}' con parámetros {}: {}",
                    method.getName(), params, throwable.getMessage(), throwable);
        };
    }
}
