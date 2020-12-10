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

package com.epam.pipeline.manager.cloud.azure;


import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.entity.region.AzureRegionCredentials;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.cloud.AbstractProviderScalingService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.cloud.commands.NodeUpCommand;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AzureScalingService extends AbstractProviderScalingService<AzureRegion> {

    private static final String AZURE_AUTH_LOCATION = "AZURE_AUTH_LOCATION";
    private static final String AZURE_RESOURCE_GROUP = "AZURE_RESOURCE_GROUP";
    private final PreferenceManager preferenceManager;
    private final CloudRegionManager cloudRegionManager;

    public AzureScalingService(final CommonCloudInstanceService instanceService,
                               final ClusterCommandService commandService,
                               final PreferenceManager preferenceManager,
                               final CloudRegionManager regionManager,
                               final ParallelExecutorService executorService,
                               @Value("${cluster.azure.nodeup.script:}") final String nodeUpScript,
                               @Value("${cluster.azure.nodedown.script:}") final String nodeDownScript,
                               @Value("${cluster.azure.reassign.script:}") final String nodeReassignScript,
                               @Value("${cluster.azure.node.terminate.script:}") final String nodeTerminateScript) {
        super(commandService, instanceService, executorService, nodeUpScript, nodeDownScript, nodeReassignScript,
              nodeTerminateScript);
        this.cloudRegionManager = regionManager;
        this.preferenceManager = preferenceManager;
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AZURE;
    }

    @Override
    public void scaleDownPoolNode(final AzureRegion region, final String nodeLabel) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, nodeLabel, null);
        runAsync(() -> instanceService.runNodeDownScript(cmdExecutor, command, buildScriptEnvVars(region)));
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final AzureRegion region) {
        final AzureRegionCredentials credentials = cloudRegionManager.loadCredentials(region);
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        envVars.put(SystemParams.CLOUD_ACCOUNT_PREFIX + region.getId(), region.getStorageAccount());
        envVars.put(SystemParams.CLOUD_ACCOUNT_KEY_PREFIX + region.getId(), credentials.getStorageAccountKey());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        return envVars;
    }

    @Override
    protected Map<String, String> buildScriptEnvVars(final AzureRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        if (StringUtils.isNotBlank(region.getAuthFile())) {
            envVars.put(AZURE_AUTH_LOCATION, region.getAuthFile());
        }
        envVars.put(AZURE_RESOURCE_GROUP, region.getResourceGroup());
        return envVars;
    }

    @Override
    protected void extendNodeUpScript(final NodeUpCommand.NodeUpCommandBuilder commandBuilder, final AzureRegion region,
                                      final RunInstance instance) {
        commandBuilder.sshKey(region.getSshPublicKeyPath());
        final Boolean clusterSpotStrategy = instance.getSpot() == null
                                            ? preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT)
                                            : instance.getSpot();
        if (BooleanUtils.isTrue(clusterSpotStrategy)) {
            commandBuilder.isSpot(true);
        }
    }
}
