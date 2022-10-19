package com.epam.pipeline.vmmonitor.config;

import com.epam.pipeline.entity.reporter.NodeReporterStatsType;
import com.epam.pipeline.vmmonitor.model.instance.NodeThresholdEvent;
import com.epam.pipeline.vmmonitor.service.DelayNotifier;
import com.epam.pipeline.vmmonitor.service.Notifier;
import com.epam.pipeline.vmmonitor.service.node.NodeMonitor;
import com.epam.pipeline.vmmonitor.service.node.NodeThresholdNotifier;
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import com.epam.pipeline.vmmonitor.service.pipeline.NodeStatsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnProperty(value = "monitor.node.enable", havingValue = "true")
public class NodeMonitorConfiguration {

    @Bean
    public NodeMonitor nodeMonitor(
            final Notifier<List<NodeThresholdEvent>> notifier,
            final NodeStatsClient nodeStatsClient,
            @Value("${monitor.node.namespace:default}") final String namespace,
            @Value("${monitor.node.monitoring.node.label:cloud-pipeline/cp-node-monitor}")
            final String monitoringNodeLabel,
            @Value("${monitor.node.reporting.pod.name:cp-node-reporter}") final String reportingPodName,
            @Value("#{${monitor.node.thresholds:{NOFILE:60}}}") final Map<NodeReporterStatsType, Long> thresholds,
            @Value("${monitor.node.pool.size:1}") final int poolSize) {
        return new NodeMonitor(notifier, nodeStatsClient, Executors.newFixedThreadPool(poolSize),
                namespace, monitoringNodeLabel, reportingPodName, thresholds);
    }

    @Bean
    public Notifier<List<NodeThresholdEvent>> nodeNotifier(
            final VMNotificationService notificationService,
            @Value("${notification.node.threshold.subject}") final String subject,
            @Value("${notification.node.threshold.template}") final String template,
            @Value("${notification.node.threshold.resend.delay.mins:120}") final int timeoutMinutes) {
        final Notifier<List<NodeThresholdEvent>> notifier = new NodeThresholdNotifier(notificationService, subject,
                template);
        return new DelayNotifier<>(notifier, Duration.ofMinutes(timeoutMinutes));
    }

    @Bean
    public NodeStatsClient nodeStatsClient(
            @Value("${monitor.node.stats.request.schema:http}") final String schema,
            @Value("${monitor.node.stats.request.port:8000}") final int port) {
        return new NodeStatsClient(schema, port);
    }
}
