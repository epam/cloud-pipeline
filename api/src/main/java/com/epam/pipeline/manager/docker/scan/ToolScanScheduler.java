/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.docker.scan;

import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Future;

/**
 * A service class, that schedules Tool security scanning for vulnerabilities. The Tool can be scheduled for scanning
 * either in a regular fashion, when all the Tools are
 */
@Service
public class ToolScanScheduler extends AbstractSchedulingManager {
    private final ToolScanSchedulerCore core;

    @Autowired
    public ToolScanScheduler(final ToolScanSchedulerCore core) {
        this.core = core;
    }

    @PostConstruct
    public void init() {
        scheduleSecured(core::scheduledToolScan, SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_SCHEDULE_CRON,
                "Tool Security Scan");
    }

    @PreDestroy
    public void shutDown() {
        core.shutDown();
    }

    /**
     * A scheduled scan, that runs for all the registries, all tools and all tool versions, sends them to Tool Scanning
     * System and stores scanning results to the database.
     */
    public void scheduledToolScan() {
        core.scheduledToolScan();
    }

    /**
     * Schedule a Tool for security scan. Since a Tool's scan is a time costly operation, there's a queue for that.
     * A tool is added to that queue and will be processed in order. Once the tool is added to a queue, it's scanStatus
     * field is being set to {@link ToolScanStatus}.PENDING
     *
     * @param registry a registry path, where tool is located
     * @param id       Tool's id or image
     * @param version  Tool's version (Docker tag)
     * @param rescan
     */
    public Future<ToolVersionScanResult> forceScheduleScanTool(final String registry, final String id,
                                                               final String version, final Boolean rescan) {
        return core.forceScheduleScanTool(registry, id, version, rescan);
    }

}
