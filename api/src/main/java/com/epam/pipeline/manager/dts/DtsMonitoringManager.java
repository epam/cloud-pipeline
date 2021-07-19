package com.epam.pipeline.manager.dts;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsStatus;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DtsMonitoringManager extends AbstractSchedulingManager {

    private static final Duration FALLBACK_OFFLINE_TIMEOUT = Duration.ofMinutes(5);

    private final DtsRegistryManager registryManager;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    @PostConstruct
    public void init() {
        scheduleFixedDelaySecured(this::monitor, SystemPreferences.DTS_MONITORING_PERIOD_SECONDS,
                TimeUnit.SECONDS, "DTS Monitoring");
    }

    private void monitor() {
        final LocalDateTime offlineThreshold = DateUtils.nowUTC().minus(getOfflineTimeout());
        for (final DtsRegistry registry : registryManager.loadAll()) {
            final LocalDateTime heartbeat = Optional.ofNullable(registry.getHeartbeat()).orElse(LocalDateTime.MIN);
            if (heartbeat.isBefore(offlineThreshold)) {
                registry.setStatus(DtsStatus.OFFLINE);
                registryManager.updateStatus(registry.getId(), registry.getStatus());
            }
            log.debug(messageHelper.getMessage(MessageConstants.INFO_DTS_MONITORING_STATUS,
                    registry.getName(), registry.getId(), registry.getStatus(), registry.getHeartbeat()));
        }
    }

    private Duration getOfflineTimeout() {
        return Optional.of(SystemPreferences.DTS_OFFLINE_TIMEOUT_SECONDS)
                .map(AbstractSystemPreference::getKey)
                .map(preferenceManager::getIntPreference)
                .map(Duration::ofSeconds)
                .orElse(FALLBACK_OFFLINE_TIMEOUT);
    }
}
