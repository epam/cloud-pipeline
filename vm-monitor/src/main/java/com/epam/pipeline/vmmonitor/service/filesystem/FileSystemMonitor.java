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
import com.epam.pipeline.vmmonitor.service.Monitor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileSystemMonitor implements Monitor {

    private final FileSystemNotifier notifier;
    private final Map<String, Double> thresholdMapping;

    @Autowired
    public FileSystemMonitor(final FileSystemNotifier notifier,
                             @Value("#{${monitor.filesystem.scan.configuration:{}}}")
                             final Map<String, Double> thresholdMapping) {
        this.notifier = notifier;
        this.thresholdMapping = thresholdMapping;
    }

    @Override
    public void monitor() {
        final List<FileSystemUsageSummary> fsPathsExceedingLimits = thresholdMapping.entrySet()
            .stream()
            .map(this::mapToFileSystemUsageSummary)
            .filter(FileSystemUsageSummary::exceedsThreshold)
            .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(fsPathsExceedingLimits)) {
            notifier.notifyFilesystemThresholds(fsPathsExceedingLimits);
        } else {
            log.debug("None of the target paths exceeds threshold configured.");
        }
    }

    private FileSystemUsageSummary mapToFileSystemUsageSummary(final Map.Entry<String, Double> fsMonitoringRule) {
        final String path = fsMonitoringRule.getKey();
        final Double percentageThreshold = fsMonitoringRule.getValue();
        final File fsPath = new File(path);
        final long totalSpaceBytes = fsPath.getTotalSpace();
        final long usedSpaceBytes = totalSpaceBytes - fsPath.getFreeSpace();
        return new FileSystemUsageSummary(path, usedSpaceBytes, totalSpaceBytes, percentageThreshold);
    }
}
