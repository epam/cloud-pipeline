package com.epam.pipeline.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(value = "server.shutdown", havingValue = "graceful")
public class GracefulShutdownConfiguration {

    @Bean
    public GracefulShutdownListener gracefulShutdownListener(
            @Value("${spring.lifecycle.timeout-per-shutdown-phase:30s}")
            final String gracefulShutdownTimeoutString) {
        final long gracefulShutdownTimeoutInSeconds = toSeconds(gracefulShutdownTimeoutString);
        return new GracefulShutdownListener(gracefulShutdownTimeoutInSeconds);
    }

    private int toSeconds(final String timeoutString) {
        return NumberUtils.toInt(StringUtils.removeEnd(timeoutString, "s"));
    }

//    @Bean
//    public ServletWebServerFactoryCustomizer embeddedServletContainerCustomizer(
//            final GracefulShutdownListener gracefulShutdownListener) {
//        Optional.of(container)
//                .filter(TomcatServletWebServerFactory.class::isInstance)
//                .map(TomcatServletWebServerFactory.class::cast)
//                .ifPresent(factory -> factory.addConnectorCustomizers(gracefulShutdownListener));
//        return servletWebServerFactoryCustomizer;
//    }

    @Slf4j
    @RequiredArgsConstructor
    private static class GracefulShutdownListener implements TomcatConnectorCustomizer,
            ApplicationListener<ContextClosedEvent> {

        private final long timeoutSeconds;

        private volatile Connector connector;

        @Override
        public void customize(final Connector connector) {
            this.connector = connector;
        }

        @Override
        public void onApplicationEvent(final ContextClosedEvent event) {
            log.info("Gracefully shutting down application...");

            log.debug("Resetting new connections...");
            connector.pause();

            log.debug("Terminating connections pool...");
            getThreadPoolExecutor(connector).ifPresent(this::terminate);

            log.debug("Proceeding with application shutting down...");
        }

        private Optional<ThreadPoolExecutor> getThreadPoolExecutor(final Connector connector) {
            return Optional.of(connector)
                    .map(Connector::getProtocolHandler)
                    .map(ProtocolHandler::getExecutor)
                    .filter(ThreadPoolExecutor.class::isInstance)
                    .map(ThreadPoolExecutor.class::cast);
        }

        private void terminate(final ThreadPoolExecutor executor) {
            try {
                executor.shutdown();
                log.debug("Waiting for connections pool to terminate...");
                if (executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                    log.debug("Connections pool has been terminated successfully.");
                } else {
                    log.warn("Connections pool has not been terminated after %s seconds. " +
                            "Proceeding with forceful connections shutting down...");
                }
            } catch (InterruptedException e) {
                log.warn("Connections pool termination has been interrupted. " +
                        "Proceeding with forceful connections shutting down...");
                Thread.currentThread().interrupt();
            }
        }
    }
}
