/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.dts.sync.service;

import com.epam.pipeline.dto.dts.transfer.TransferDTO;
import com.epam.pipeline.dts.TestUtils;
import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.common.service.IdentificationService;
import com.epam.pipeline.dts.sync.service.impl.CloudPipelineEventReportingService;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.rest.mapper.TransferTaskMapper;
import com.epam.pipeline.dts.transfer.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class CloudPipelineEventReportingServiceTest {

    private static final String TEST_ID = "DTS";

    private final CloudPipelineAPIClient api = mock(CloudPipelineAPIClient.class);
    private final IdentificationService identificationService = mock(IdentificationService.class);
    private final PreferenceService preferenceService = mock(PreferenceService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TransferTaskMapper taskMapper = mock(TransferTaskMapper.class);
    private final EventReportingService service = new CloudPipelineEventReportingService(api, identificationService,
            preferenceService, taskService, taskMapper);
    private final TransferDTO transferDTO = new TransferDTO();
    private final LocalDateTime updateTime = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        doReturn(TEST_ID).when(identificationService).getId();
        final TransferTask transferTask = TransferTask.builder()
                .id(1L)
                .build();
        doReturn(Collections.singletonList(transferTask)).when(taskService).loadUpdated(any());
        transferDTO.setId(1L);
        doReturn(transferDTO).when(taskMapper).modelToDto(transferTask);
    }

    @Test
    void lastUpdateFromTimestampProperty() {
        doReturn(updateTime.toString()).when(preferenceService).getDtsEventReportingSyncTimestamp();
        service.report();

        verify(api).createTransferTasks(TEST_ID, Collections.singletonList(transferDTO));
    }

    @Test
    void lastUpdateFromFile() throws IOException {
        final String eventReportingSyncFile = ResourceUtils
                .getFile(TestUtils.class.getResource("/transfer/event_reporting.txt"))
                .getAbsolutePath();
        doReturn(eventReportingSyncFile).when(preferenceService).getDtsEventReportingSyncFile();
        service.report();

        verify(api).createTransferTasks(TEST_ID, Collections.singletonList(transferDTO));
    }
}
