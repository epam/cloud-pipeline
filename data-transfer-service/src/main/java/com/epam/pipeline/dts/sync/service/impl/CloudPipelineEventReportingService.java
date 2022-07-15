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

package com.epam.pipeline.dts.sync.service.impl;

import com.epam.pipeline.dto.dts.transfer.TransferDTO;
import com.epam.pipeline.dts.common.service.CloudPipelineAPIClient;
import com.epam.pipeline.dts.common.service.IdentificationService;
import com.epam.pipeline.dts.sync.service.EventReportingService;
import com.epam.pipeline.dts.sync.service.PreferenceService;
import com.epam.pipeline.dts.transfer.model.TransferTask;
import com.epam.pipeline.dts.transfer.rest.mapper.TransferTaskMapper;
import com.epam.pipeline.dts.transfer.service.TaskService;
import com.epam.pipeline.dts.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudPipelineEventReportingService implements EventReportingService {

    private final CloudPipelineAPIClient api;
    private final IdentificationService identificationService;
    private final PreferenceService preferenceService;
    private final TaskService taskService;
    private final TransferTaskMapper taskMapper;

    @Override
    @Scheduled(cron = "${dts.event.reporting.enabled}")
    public void report() {
        final LocalDateTime lastUpdateTime = getLastUpdateTime();
        log.debug("Sending transfer tasks");
        final List<TransferDTO> tasks = new ArrayList<>();
        for (TransferTask transferTask : taskService.loadUpdated(lastUpdateTime)) {
            TransferDTO transferDTO = taskMapper.modelToDto(transferTask);
            tasks.add(transferDTO);
        }
        if (!CollectionUtils.isEmpty(tasks)) {
            api.createTransferTasks(identificationService.getId(), tasks);
            updateDtsEventReportingSyncFile();
        }
    }

    private LocalDateTime getLastUpdateTime(){
        final String eventReportingSyncFile = preferenceService.getDtsEventReportingSyncFile();
        if (StringUtils.isNotBlank(eventReportingSyncFile)) {
            final Path path = Paths.get(eventReportingSyncFile);
            if (Utils.fileExists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    return LocalDateTime.parse(IOUtils.toString(reader));
                } catch (DateTimeParseException|IOException e) {
                    log.debug("Unable to get last update timestamp from file");
                }
            }
        }
        final String eventReportingSyncTimestamp = preferenceService.getDtsEventReportingSyncTimestamp();
        if (StringUtils.isNotBlank(eventReportingSyncTimestamp)) {
            try {
                return LocalDateTime.parse(eventReportingSyncTimestamp);
            } catch (DateTimeParseException ex1) {
                log.debug("Unable to get last update timestamp from property");
            }
        }
        return LocalDateTime.now();
    }

    private void updateDtsEventReportingSyncFile(){
        final String eventReportingSyncFile = preferenceService.getDtsEventReportingSyncFile();
        if (StringUtils.isNotBlank(eventReportingSyncFile)) {
            try (FileWriter writer = new FileWriter(eventReportingSyncFile)) {
                writer.write(LocalDateTime.now().toString());
            } catch (IOException e) {
                log.debug("Unable to write update timestamp to file");
            }
        }
    }
}
