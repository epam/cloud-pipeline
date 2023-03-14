package com.epam.pipeline.app;

import com.epam.pipeline.manager.audit.AuditConsumer;
import com.epam.pipeline.manager.audit.AuditContainer;
import com.epam.pipeline.manager.audit.AuditDaemon;
import com.epam.pipeline.manager.audit.BlockingAuditContainer;
import com.epam.pipeline.manager.audit.LogAuditConsumer;
import com.epam.pipeline.manager.audit.BufferingAuditDaemon;
import com.epam.pipeline.manager.log.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

@Configuration
public class AuditConfiguration {

    @Bean
    public AuditContainer auditContainer(@Value("${audit.container.capacity:10000}") final int capacity) {
        return new BlockingAuditContainer(capacity);
    }

    @Bean
    public AuditConsumer auditConsumer(final LogManager logManager) throws UnknownHostException {
        final String host = InetAddress.getLocalHost().getHostName();
        return new LogAuditConsumer(host, logManager);
    }

    @Bean
    public AuditDaemon auditDaemon(final AuditContainer auditContainer,
                                   final AuditConsumer auditConsumer,
                                   @Value("${audit.consumer.buffer.time:60}") final int time,
                                   @Value("${audit.consumer.buffer.size:1000}") final int size) {
        return new BufferingAuditDaemon(auditContainer, auditConsumer, time, size);
    }

    @Bean
    public ApplicationListener<ContextRefreshedEvent> auditDaemonLauncher(final AuditDaemon daemon) {
        return event -> {
            if (Objects.isNull(event.getApplicationContext().getParent())) {
                daemon.start();
            }
        };
    }

//    @Bean
//    public AuditDaemonLauncher auditDaemonLauncher(final AuditDaemon daemon) {
//        return new AuditDaemonLauncher(daemon);
//    }
//
//    @RequiredArgsConstructor
//    private static class AuditDaemonLauncher implements ApplicationListener<ContextRefreshedEvent> {
//
//        private final AuditDaemon daemon;
//
//        @Override
//        public void onApplicationEvent(final ContextRefreshedEvent event) {
//            if (Objects.isNull(event.getApplicationContext().getParent())) {
//                daemon.start();
//            }
//        }
//    }
}
