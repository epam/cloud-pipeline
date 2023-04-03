package com.epam.pipeline.app;

import com.epam.pipeline.manager.audit.AuditClient;
import com.epam.pipeline.manager.datastorage.providers.StorageEventCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {

    @Bean
    public StorageEventCollector s3Events(final AuditClient s3AuditClient) {
        return new StorageEventCollector(s3AuditClient);
    }

    @Bean
    public StorageEventCollector azEvents(final AuditClient azAuditClient) {
        return new StorageEventCollector(azAuditClient);
    }

    @Bean
    public StorageEventCollector gsEvents(final AuditClient gsAuditClient) {
        return new StorageEventCollector(gsAuditClient);
    }
}
