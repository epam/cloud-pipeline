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

package com.epam.pipeline.manager.cloud.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudScalingService;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.cloud.commands.NodeUpCommand;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.execution.SystemParams;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AWSScalingService implements CloudScalingService<AwsRegion> {

    private static final String MANUAL = "manual";
    private static final String ON_DEMAND = "on_demand";

    private final EC2Helper ec2Helper;
    private final PreferenceManager preferenceManager;
    private final InstanceOfferManager instanceOfferManager;
    private final CommonCloudInstanceService instanceService;
    private final ClusterCommandService commandService;
    private final String nodeUpScript;
    private final String nodeDownScript;
    private final String nodeReassignScript;
    private final String nodeTerminateScript;
    private final CmdExecutor cmdExecutor = new CmdExecutor();

    // TODO: 25-10-2019 @Lazy annotation added to resolve issue with circular dependency.
    // It would be great fix this issue by actually removing this dependency:
    // CloudFacade  -> AWSInstanceService -> InstanceOfferManager -> CloudFacade
    public AWSScalingService(final EC2Helper ec2Helper,
                             final PreferenceManager preferenceManager,
                             final @Lazy InstanceOfferManager instanceOfferManager,
                             final CommonCloudInstanceService instanceService,
                             final ClusterCommandService commandService,
                             @Value("${cluster.nodeup.script}") final String nodeUpScript,
                             @Value("${cluster.nodedown.script}") final String nodeDownScript,
                             @Value("${cluster.reassign.script}") final String nodeReassignScript,
                             @Value("${cluster.node.terminate.script}") final String nodeTerminateScript) {
        this.ec2Helper = ec2Helper;
        this.preferenceManager = preferenceManager;
        this.instanceOfferManager = instanceOfferManager;
        this.instanceService = instanceService;
        this.commandService = commandService;
        this.nodeUpScript = nodeUpScript;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final AwsRegion region,
                                   final Long runId,
                                   final RunInstance instance) {
        final String command = buildNodeUpCommand(region, String.valueOf(runId), instance, Collections.emptyMap());
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, buildScriptEnvVars());
    }

    @Override
    public RunInstance scaleUpPoolNode(final AwsRegion region,
                                       final String nodeIdLabel,
                                       final NodePool node) {
        final RunInstance instance = node.toRunInstance();
        final String command = buildNodeUpCommand(region, nodeIdLabel, instance, getPoolLabels(node));
        return instanceService.runNodeUpScript(cmdExecutor, null, instance, command, buildScriptEnvVars());
    }

    @Override
    public void scaleDownNode(final AwsRegion region, final Long runId) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, runId, getProviderName());
        instanceService.runNodeDownScript(cmdExecutor, command, buildScriptEnvVars());
    }

    @Override
    public void scaleDownPoolNode(final AwsRegion region, final String nodeLabel) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, nodeLabel, getProviderName());
        instanceService.runNodeDownScript(cmdExecutor, command, buildScriptEnvVars());
    }

    @Override
    public boolean reassignNode(final AwsRegion region, final Long oldId, final Long newId) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, oldId, newId, getProvider().name());
        return instanceService.runNodeReassignScript(cmdExecutor, command, oldId, newId, buildScriptEnvVars());
    }

    @Override
    public boolean reassignPoolNode(final AwsRegion region, final String nodeLabel, final Long newId) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, nodeLabel, newId, getProvider().name());
        return instanceService.runNodeReassignScript(cmdExecutor, command, nodeLabel,
                String.valueOf(newId), buildScriptEnvVars());
    }

    @Override
    public void terminateNode(final AwsRegion region, final String internalIp, final String nodeName) {
        final String command = commandService.buildTerminateNodeCommand(nodeTerminateScript, internalIp, nodeName,
                getProviderName());
        instanceService.runTerminateNodeScript(command, cmdExecutor, buildScriptEnvVars());
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final AwsRegion region, final Long runId) {
        return ec2Helper.getInstanceLaunchTime(String.valueOf(runId), region.getRegionCode());
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final AwsRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        final AWSCredentials credentials = AWSUtils.getCredentialsProvider(region.getProfile()).getCredentials();
        envVars.put(SystemParams.CLOUD_ACCOUNT_PREFIX + region.getId(), credentials.getAWSAccessKeyId());
        envVars.put(SystemParams.CLOUD_ACCOUNT_KEY_PREFIX + region.getId(), credentials.getAWSSecretKey());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        if (credentials instanceof BasicSessionCredentials) {
            envVars.put(SystemParams.CLOUD_ACCOUNT_TOKEN_PREFIX + region.getId(),
                    ((BasicSessionCredentials) credentials).getSessionToken());
        }
        return envVars;
    }

    private String buildNodeUpCommand(final AwsRegion region,
                                      final String nodeLabel,
                                      final RunInstance instance,
                                      final Map<String, String> labels) {
        final NodeUpCommand.NodeUpCommandBuilder commandBuilder =
                commandService.buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, getProviderName(), labels)
                               .sshKey(region.getSshKeyName());

        if (StringUtils.isNotBlank(region.getKmsKeyId())) {
            commandBuilder.encryptionKey(region.getKmsKeyId());
        }
        addSpotArguments(instance, commandBuilder, region.getId());
        return commandBuilder.build().getCommand();
    }

    private void addSpotArguments(final RunInstance instance,
                                  final NodeUpCommand.NodeUpCommandBuilder command,
                                  final Long regionId) {
        final Boolean instanceSpotFlag = instance.getSpot();
        final boolean isSpot = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT);

        final boolean useSpot = (instanceSpotFlag == null && isSpot)
                || (instanceSpotFlag != null && instanceSpotFlag);
        if (useSpot) {
            setBidPrice(command, instance.getNodeType(), regionId);
        }
    }

    private Double customizeSpotArguments(final String instanceType, final Long regionId) {
        final String spotAllocStrategy = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT_ALLOC_STRATEGY);
        final Double bidPrice = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT_BID_PRICE);
        switch (spotAllocStrategy) {
            case MANUAL:
                if (bidPrice == null) {
                    log.error("Spot price must be specified in \'" + MANUAL + "\' case.");
                }
                return bidPrice;
            case ON_DEMAND:
                return instanceOfferManager.getPricePerHourForInstance(instanceType, regionId);
            default:
                log.error("Argument spot_alloc_strategy must have \'" + MANUAL + "\' or \'"
                        + ON_DEMAND + "\' value.");
                return bidPrice;
        }
    }

    private void setBidPrice(final NodeUpCommand.NodeUpCommandBuilder commandBuilder,
                             final String preference,
                             final Long id) {
        final Double bidPrice = customizeSpotArguments(preference, id);
        commandBuilder.isSpot(true);
        commandBuilder.bidPrice(Optional.ofNullable(bidPrice)
                .map(p -> String.valueOf(bidPrice)).orElse(StringUtils.EMPTY));
    }

    private Map<String, String> buildScriptEnvVars() {
        return Collections.emptyMap();
    }
}
