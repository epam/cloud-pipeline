package com.epam.pipeline.dts.sync.service;

import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.common.service.IdentificationService;
import com.epam.pipeline.dts.sync.service.impl.CloudPipelineHeartbeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CloudPipelineHeartbeatServiceTest {

    private static final String TEST_ID = "DTS";

    private final CloudPipelineAPIClient api = mock(CloudPipelineAPIClient.class);
    private final IdentificationService identificationService = mock(IdentificationService.class);
    private final PreferenceService preferenceService = mock(PreferenceService.class);
    private final HeartbeatService service = new CloudPipelineHeartbeatService(api, identificationService,
            preferenceService);

    @BeforeEach
    void setUp() {
        doReturn(TEST_ID).when(identificationService).getId();
    }

    @Test
    void heartbeatShouldSendRequestIfHeartbeatIsEnabled() {
        doReturn(true).when(preferenceService).isHeartbeatEnabled();

        service.heartbeat();

        verify(api).updateDtsRegistryHeartbeat(TEST_ID);
    }

    @Test
    void heartbeatShouldNotSendRequestIfHeartbeatIsDisabled() {
        doReturn(false).when(preferenceService).isHeartbeatEnabled();

        service.heartbeat();

        verify(api, times(0)).updateDtsRegistryHeartbeat(TEST_ID);
    }
}
