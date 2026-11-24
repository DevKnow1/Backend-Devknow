package com.devknow.config;

import com.devknow.auth.JwtService;
import com.devknow.document.FileValidator;
import com.devknow.ingestion.Chunker;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig {

    @Bean
    public JwtService jwtService(AppProperties p) {
        return new JwtService(p.jwt().secret(), p.jwt().expiration());
    }

    @Bean
    public Chunker chunker(AppProperties p) {
        return new Chunker(p.ingestion().chunkSize(), p.ingestion().chunkOverlap());
    }

    @Bean
    public FileValidator fileValidator(AppProperties p) {
        return new FileValidator(p.ingestion().maxFileSize().toBytes());
    }

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("ingestion-");
        return ex;
    }
}
