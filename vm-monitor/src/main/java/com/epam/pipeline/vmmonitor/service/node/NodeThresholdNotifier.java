package com.epam.pipeline.vmmonitor.service.node;

import com.epam.pipeline.vmmonitor.model.instance.NodeThresholdEvent;
import com.epam.pipeline.vmmonitor.service.Notifier;
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class NodeThresholdNotifier implements Notifier<List<NodeThresholdEvent>> {

    private final VMNotificationService notificationService;
    private final String subject;
    private final String template;

    @Override
    public void notify(final List<NodeThresholdEvent> events) {
        Optional.ofNullable(events)
                .filter(CollectionUtils::isNotEmpty)
                .map(it -> Collections.<String, Object>singletonMap("events", it))
                .ifPresent(params -> notificationService.sendMessage(params, subject, template));
    }
}
