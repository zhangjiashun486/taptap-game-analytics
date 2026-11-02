package org.example.base_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 采集任务线程池；第二阶段执行 DEMO，第三阶段替换为 Python 进程。 */
@Configuration
public class AsyncConfig {

    @Bean(name = "crawlTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService crawlTaskExecutor() {
        return Executors.newCachedThreadPool();
    }
}