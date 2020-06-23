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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolScanStatus;
import com.epam.pipeline.entity.scan.ToolVersionScanResult;
import com.epam.pipeline.exception.ToolScanExternalServiceException;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.docker.DockerClient;
import com.epam.pipeline.manager.docker.DockerClientFactory;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.manager.docker.ToolVersionManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.scheduling.AbstractSchedulingManager;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * A service class, that schedules Tool security scanning for vulnerabilities. The Tool can be scheduled for scanning
 * either in a regular fashion, when all the Tools are
 */
@Service
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class ToolScanScheduler extends AbstractSchedulingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolScanScheduler.class);

    private final ToolScanSchedulerCore core;

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

    @Component
    static class ToolScanSchedulerCore {

        private final DockerRegistryDao dockerRegistryDao;
        private final ToolScanManager toolScanManager;
        private final ToolManager toolManager;
        private final MessageHelper messageHelper;
        private final ToolVersionManager toolVersionManager;
        private final DockerClientFactory dockerClientFactory;
        private final DockerRegistryManager dockerRegistryManager;
        private final PreferenceManager preferenceManager;
        private final ExecutorService forceScanExecutor = Executors.newSingleThreadExecutor();

        @Autowired
        ToolScanSchedulerCore(final DockerRegistryDao dockerRegistryDao,
                              final ToolScanManager toolScanManager,
                              final ToolManager toolManager,
                              final MessageHelper messageHelper,
                              final ToolVersionManager toolVersionManager,
                              final DockerClientFactory dockerClientFactory,
                              final DockerRegistryManager dockerRegistryManager,
                              final PreferenceManager preferenceManager) {
            this.dockerRegistryDao = dockerRegistryDao;
            this.toolScanManager = toolScanManager;
            this.toolManager = toolManager;
            this.messageHelper = messageHelper;
            this.toolVersionManager = toolVersionManager;
            this.dockerClientFactory = dockerClientFactory;
            this.dockerRegistryManager = dockerRegistryManager;
            this.preferenceManager = preferenceManager;
        }

        @PreDestroy
        public void shutDown() {
            forceScanExecutor.shutdownNow();
        }

        @SchedulerLock(name = "ToolScanScheduler_scheduledToolScan", lockAtMostForString = "PT48H")
        public void scheduledToolScan() {
            if (!preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED)) {
                LOGGER.info(messageHelper.getMessage(MessageConstants.ERROR_TOOL_SCAN_DISABLED));
                return;
            } else {
                LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_TOOL_SCAN_SCHEDULED_STARTED));
            }
            boolean scanAllRegistries = preferenceManager.getPreference(
                    SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ALL_REGISTRIES);
            List<DockerRegistry> registries = scanAllRegistries ? dockerRegistryDao.loadAllDockerRegistry() :
                    dockerRegistryDao.loadDockerRegistriesWithSecurityScanEnabled();
            for (DockerRegistry registry : registries) {
                scanRegistry(registry);
            }

            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_TOOL_SCAN_SCHEDULED_DONE));
        }

        private void scanRegistry(final DockerRegistry registry) {
            LOGGER.info(messageHelper.getMessage(MessageConstants.INFO_TOOL_SCAN_REGISTRY_STARTED, registry.getPath()));
            registry.getTools()
                    .stream()
                    .filter(Tool::isNotSymlink)
                    .forEach(tool -> scanTool(registry, tool));
        }

        private void scanTool(final DockerRegistry registry, final Tool tool) {
            DockerClient dockerClient = getDockerClient(registry, tool);
            try {
                List<String> versions = toolManager.loadTags(tool.getId());
                for (String version : versions) {
                    try {
                        ToolVersionScanResult result = toolScanManager.scanTool(tool, version, false);
                        toolManager.updateToolVulnerabilities(result.getVulnerabilities(), tool.getId(), version);
                        toolManager.updateToolDependencies(result.getDependencies(), tool.getId(), version);
                        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.COMPLETED, new Date(),
                                version, result.getToolOSVersion(),
                                result.getLastLayerRef(), result.getDigest());
                        updateToolVersion(tool, version, registry, dockerClient);
                    } catch (ToolScanExternalServiceException e) {
                        LOGGER.error(messageHelper.getMessage(MessageConstants.ERROR_TOOL_SCAN_FAILED,
                                tool.getImage(), version), e);
                        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.FAILED, new Date(),
                                version, null, null);
                    }
                }
            } catch (Exception e) {
                LOGGER.error(messageHelper.getMessage(MessageConstants.ERROR_TOOL_SCAN_FAILED, tool.getImage()), e);
                toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.FAILED, new Date(),
                        "latest", null, null);
            }
        }

        private Future<ToolVersionScanResult> forceScheduleScanTool(final String registry, final String id,
                                                                    final String version, final Boolean rescan) {
            if (!preferenceManager.getPreference(SystemPreferences.DOCKER_SECURITY_TOOL_SCAN_ENABLED)) {
                throw new IllegalArgumentException(messageHelper.getMessage(MessageConstants.ERROR_TOOL_SCAN_DISABLED));
            }

            Tool tool = toolManager.loadTool(registry, id);
            Assert.isTrue(tool.isNotSymlink(), messageHelper.getMessage(
                    MessageConstants.ERROR_TOOL_SYMLINK_MODIFICATION_NOT_SUPPORTED));
            Optional<ToolVersionScanResult> toolVersionScanResult = toolManager.loadToolVersionScan(
                    tool.getId(), version);
            ToolScanStatus curentStatus = toolVersionScanResult
                    .map(ToolVersionScanResult::getStatus)
                    .orElse(ToolScanStatus.NOT_SCANNED);
            // The tool is already in the queue
            if (curentStatus != ToolScanStatus.PENDING) {
                String layerRef = toolVersionScanResult
                        .map(ToolVersionScanResult::getLastLayerRef)
                        .orElse(null);
                String digest = toolVersionScanResult
                        .map(ToolVersionScanResult::getDigest)
                        .orElse(null);
                toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.PENDING, null,
                        version, layerRef, digest);
                return forceScanExecutor.submit(new DelegatingSecurityContextCallable<>(() -> {
                    LOGGER.info(messageHelper.getMessage(
                            MessageConstants.INFO_TOOL_FORCE_SCAN_STARTED, tool.getImage()));

                    try {
                        ToolVersionScanResult scanResult = toolScanManager.scanTool(tool, version, rescan);
                        toolManager.updateToolVulnerabilities(scanResult.getVulnerabilities(), tool.getId(),
                                version);
                        toolManager.updateToolDependencies(scanResult.getDependencies(), tool.getId(), version);
                        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.COMPLETED,
                                scanResult.getScanDate(), version, scanResult.getToolOSVersion(),
                                scanResult.getLastLayerRef(), scanResult.getDigest());
                        return scanResult;
                    } catch (Exception e) {
                        toolManager.updateToolVersionScanStatus(tool.getId(), ToolScanStatus.FAILED, new Date(),
                                version, null, null);
                        LOGGER.error(messageHelper.getMessage(
                                MessageConstants.ERROR_TOOL_SCAN_FAILED, tool.getImage()), e);
                        throw new PipelineException(e);
                    }
                }, SecurityContextHolder.getContext()));
            }

            return CompletableFuture.completedFuture(new ToolVersionScanResult(ToolScanStatus.PENDING, null,
                    Collections.emptyList(), Collections.emptyList()));
        }

        private void updateToolVersion(Tool tool, String version, DockerRegistry registry, DockerClient dockerClient) {
            try {
                toolVersionManager.updateOrCreateToolVersion(tool.getId(), version, tool.getImage(),
                        registry, dockerClient);
            } catch (Exception e) {
                LOGGER.error(messageHelper.getMessage(MessageConstants.ERROR_UPDATE_TOOL_VERSION_FAILED,
                        tool.getImage(), version), e);
            }
        }

        private DockerClient getDockerClient(DockerRegistry registry, Tool tool) {
            String token = dockerRegistryManager.getImageToken(registry, tool.getImage());
            return dockerClientFactory.getDockerClient(registry, token);
        }
    }
}
