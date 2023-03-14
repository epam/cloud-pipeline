package com.epam.pipeline.app;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.manager.audit.AuditClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConfiguration {

    @Bean
    public AuditClient s3AuditClient() {
        return new AuditClient(DataStorageType.S3);
    }

    @Bean
    public AuditClient azAuditClient() {
        return new AuditClient(DataStorageType.AZ);
    }

    @Bean
    public AuditClient gsAuditClient() {
        return new AuditClient(DataStorageType.GS);
    }
}
