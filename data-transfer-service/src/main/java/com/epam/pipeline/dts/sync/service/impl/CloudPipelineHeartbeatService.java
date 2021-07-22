package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.common.service.IdentificationService;
import com.epam.pipeline.dts.sync.service.HeartbeatService;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudPipelineHeartbeatService implements HeartbeatService {

    private final CloudPipelineAPIClient api;
    private final IdentificationService identificationService;
    private final PreferenceService preferenceService;

    @Override
    @Scheduled(fixedDelayString = "${dts.heartbeat.poll:60000}")
    public void heartbeat() {
        if (preferenceService.isHeartbeatEnabled()) {
            log.debug("Sending heartbeat");
            api.updateDtsRegistryHeartbeat(identificationService.getId());
        }
    }
}
