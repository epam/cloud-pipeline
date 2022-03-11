/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.vmmonitor.service.filesystem;

import com.epam.pipeline.vmmonitor.model.filesystem.FileSystemUsageSummary;
import com.epam.pipeline.vmmonitor.service.notification.VMNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileSystemNotifier {

    private final VMNotificationService notificationService;
    private final String fileSystemUsageSubject;
    private final String fileSystemThresholdSubject;

    @Autowired
    public FileSystemNotifier(final VMNotificationService notificationService,
                              final @Value("${notification.filesystem.subject}") String fileSystemUsageSubject,
                              final @Value("${notification.filesystem.template}") String fileSystemThresholdSubject) {
        this.notificationService = notificationService;
        this.fileSystemUsageSubject = fileSystemUsageSubject;
        this.fileSystemThresholdSubject = fileSystemThresholdSubject;
    }

    public void notifyFilesystemThresholds(final List<FileSystemUsageSummary> thresholdSummaries) {
        final Map<String, Object> parameters = Collections.singletonMap("fileSystemUsageSummaries", thresholdSummaries);
        log.debug("Sending filesystem threshold notification on {} monitored paths", thresholdSummaries.size());
        notificationService.sendMessage(parameters, fileSystemUsageSubject, fileSystemThresholdSubject);
    }
}
