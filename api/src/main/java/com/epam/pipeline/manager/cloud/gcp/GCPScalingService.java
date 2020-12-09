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

package com.epam.pipeline.manager.cloud.gcp;

import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.manager.cloud.AbstractProviderScalingService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class GCPScalingService extends AbstractProviderScalingService<GCPRegion> {

    private static final String GOOGLE_PROJECT_ID = "GOOGLE_PROJECT_ID";
    protected static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

    private final String nodeUpScript;

    public GCPScalingService(final ClusterCommandService commandService,
                             final CommonCloudInstanceService instanceService,
                             final ParallelExecutorService executorService,
                             @Value("${cluster.gcp.nodeup.script}") final String nodeUpScript,
                             @Value("${cluster.gcp.nodedown.script}") final String nodeDownScript,
                             @Value("${cluster.gcp.reassign.script}") final String nodeReassignScript,
                             @Value("${cluster.gcp.node.terminate.script}") final String nodeTerminateScript) {
        super(commandService, instanceService, executorService, nodeDownScript, nodeReassignScript,
              nodeTerminateScript);
        this.nodeUpScript = nodeUpScript;
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        final String credentialsFile = getCredentialsFilePath(region);
        if (!StringUtils.isEmpty(credentialsFile)) {
            try {
                final String credentials = String.join(StringUtils.EMPTY,
                        Files.readAllLines(Paths.get(credentialsFile)));
                envVars.put(SystemParams.CLOUD_CREDENTIALS_FILE_CONTENT_PREFIX + region.getId(), credentials);
            } catch (IOException | InvalidPathException e) {
                log.error("Cannot read credentials file {} for region {}", region.getName(), credentialsFile);
            }
        }
        return envVars;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.GCP;
    }

    @Override
    protected Map<String, String> buildScriptEnvVars(GCPRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(GOOGLE_APPLICATION_CREDENTIALS, region.getAuthFile());
        }
        envVars.put(GOOGLE_PROJECT_ID, region.getProject());
        return envVars;
    }

    @Override
    protected String buildNodeUpCommand(final GCPRegion region, final String nodeLabel, final RunInstance instance,
                                      final Map<String, String> labels) {
        return commandService
            .buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, getProviderName())
            .sshKey(region.getSshPublicKeyPath())
            .isSpot(Optional.ofNullable(instance.getSpot())
                        .orElse(false))
            .bidPrice(StringUtils.EMPTY)
            .additionalLabels(labels)
            .prePulledImages(instance.getPrePulledDockerImages())
            .build()
            .getCommand();
    }

    private String getCredentialsFilePath(GCPRegion region) {
        return StringUtils.isEmpty(region.getAuthFile())
                ? System.getenv(GOOGLE_APPLICATION_CREDENTIALS)
                : region.getAuthFile();
    }
}
