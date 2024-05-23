/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.epam.pipeline.controller.vo.InstanceOfferRequestVO;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordFormat;
import com.epam.pipeline.entity.cloud.InstanceTerminationState;
import com.epam.pipeline.entity.cloud.CloudInstanceOperationResult;
import com.epam.pipeline.entity.cluster.InstanceDisk;
import com.epam.pipeline.entity.cluster.InstanceImage;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.pipeline.DiskAttachRequest;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.exception.cloud.aws.AwsEc2Exception;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.CloudInstanceService;
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
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Slf4j
public class AWSInstanceService implements CloudInstanceService<AwsRegion> {

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
    private final Route53Helper route53Helper;
    private final AWSCredentialsService credentialsService;
    private final CmdExecutor cmdExecutor = new CmdExecutor();

    // TODO: 25-10-2019 @Lazy annotation added to resolve issue with circular dependency.
    // It would be great fix this issue by actually removing this dependency:
    // CloudFacade  -> AWSInstanceService -> InstanceOfferManager -> CloudFacade
    public AWSInstanceService(final EC2Helper ec2Helper,
                              final PreferenceManager preferenceManager,
                              final @Lazy InstanceOfferManager instanceOfferManager,
                              final CommonCloudInstanceService instanceService,
                              final ClusterCommandService commandService,
                              final Route53Helper route53Helper,
                              final AWSCredentialsService credentialsService,
                              @Value("${cluster.nodeup.script}") final String nodeUpScript,
                              @Value("${cluster.nodedown.script}") final String nodeDownScript,
                              @Value("${cluster.reassign.script}") final String nodeReassignScript,
                              @Value("${cluster.node.terminate.script}") final String nodeTerminateScript) {
        this.ec2Helper = ec2Helper;
        this.preferenceManager = preferenceManager;
        this.instanceOfferManager = instanceOfferManager;
        this.instanceService = instanceService;
        this.commandService = commandService;
        this.route53Helper = route53Helper;
        this.credentialsService = credentialsService;
        this.nodeUpScript = nodeUpScript;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final AwsRegion region,
                                   final Long runId,
                                   final RunInstance instance,
                                   final Map<String, String> runtimeParameters,
                                   final Map<String, String> customTags) {
        final String command = buildNodeUpCommand(region, String.valueOf(runId), instance,
                Collections.emptyMap(), runtimeParameters, customTags);
        return instanceService.runNodeUpScript(cmdExecutor, runId, instance, command, buildScriptEnvVars(region));
    }

    @Override
    public RunInstance scaleUpPoolNode(final AwsRegion region,
                                       final String nodeIdLabel,
                                       final NodePool node) {
        final RunInstance instance = node.toRunInstance();
        final String command = buildNodeUpCommand(region, nodeIdLabel, instance, getPoolLabels(node),
                Collections.emptyMap(), Collections.emptyMap());
        return instanceService.runNodeUpScript(cmdExecutor, null, instance, command, buildScriptEnvVars(region));
    }

    @Override
    public void scaleDownNode(final AwsRegion region, final Long runId) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, runId, getProviderName());
        instanceService.runNodeDownScript(cmdExecutor, command, buildScriptEnvVars(region));
    }

    @Override
    public void scaleDownPoolNode(final AwsRegion region, final String nodeLabel) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, nodeLabel, getProviderName());
        instanceService.runNodeDownScript(cmdExecutor, command, buildScriptEnvVars(region));
    }

    @Override
    public boolean reassignNode(final AwsRegion region, final Long oldId, final Long newId,
                                final Map<String, String> customTags) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, oldId, newId, getProvider().name(), customTags);
        return instanceService.runNodeReassignScript(cmdExecutor, command, oldId, newId, buildScriptEnvVars(region));
    }

    @Override
    public boolean reassignPoolNode(final AwsRegion region, final String nodeLabel, final Long newId,
                                    final Map<String, String> customTags) {
        final String command = commandService.buildNodeReassignCommand(
                nodeReassignScript, nodeLabel, String.valueOf(newId), getProvider().name(), customTags);
        return instanceService.runNodeReassignScript(cmdExecutor, command, nodeLabel,
                String.valueOf(newId), buildScriptEnvVars(region));
    }

    @Override
    public void terminateNode(final AwsRegion region, final String internalIp, final String nodeName) {
        final String command = commandService.buildTerminateNodeCommand(nodeTerminateScript, internalIp, nodeName,
                getProviderName());
        instanceService.runTerminateNodeScript(command, cmdExecutor, buildScriptEnvVars(region));
    }

    @Override
    public CloudInstanceOperationResult startInstance(final AwsRegion region, final String instanceId) {
        log.debug("Starting AWS instance {}", instanceId);
        return ec2Helper.startInstance(instanceId, region);
    }

    @Override
    public void stopInstance(final AwsRegion region, final String instanceId) {
        log.debug("Stopping AWS instance {}", instanceId);
        ec2Helper.stopInstance(instanceId, region);
    }

    @Override
    public void terminateInstance(final AwsRegion region, final String instanceId) {
        log.debug("Terminating AWS instance {}", instanceId);
        ec2Helper.terminateInstance(instanceId, region);
    }

    @Override
    public boolean instanceExists(final AwsRegion region, final String instanceId) {
        log.debug("Checking if AWS instance {} exists", instanceId);
        return ec2Helper.findInstance(instanceId, region).isPresent();
    }

    @Override
    public CloudProvider getProvider() {
        return CloudProvider.AWS;
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final AwsRegion region, final Long runId) {
        return ec2Helper.getInstanceLaunchTime(String.valueOf(runId), region);
    }

    @Override
    public RunInstance describeInstance(final AwsRegion region,
                                        final String nodeLabel,
                                        final RunInstance instance) {
        return describeInstance(nodeLabel, instance,
            () -> ec2Helper.getActiveInstance(nodeLabel, region));
    }

    @Override
    public RunInstance describeAliveInstance(final AwsRegion region,
                                             final String nodeLabel,
                                             final RunInstance instance) {
        return describeInstance(nodeLabel, instance,
            () -> ec2Helper.getAliveInstance(nodeLabel, region));
    }

    private RunInstance describeInstance(final String nodeLabel,
                                         final RunInstance instance,
                                         final Supplier<Instance> supplier) {
        log.debug("Getting instance description for label {}.", nodeLabel);
        try {
            final Instance ec2Instance = supplier.get();
            instance.setNodeId(ec2Instance.getInstanceId());
            instance.setNodeIP(ec2Instance.getPrivateIpAddress());
            instance.setNodeName(ec2Instance.getInstanceId());
            return instance;
        } catch (AwsEc2Exception e) {
            log.debug("Instance for label {} not found", nodeLabel);
            log.trace(e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Map<String, String> buildContainerCloudEnvVars(final AwsRegion region) {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put(SystemParams.CLOUD_REGION_PREFIX + region.getId(), region.getRegionCode());
        final AWSCredentials credentials = AWSUtils.getCredentialsProvider(region).getCredentials();
        envVars.put(SystemParams.CLOUD_ACCOUNT_PREFIX + region.getId(), credentials.getAWSAccessKeyId());
        envVars.put(SystemParams.CLOUD_ACCOUNT_KEY_PREFIX + region.getId(), credentials.getAWSSecretKey());
        envVars.put(SystemParams.CLOUD_PROVIDER_PREFIX + region.getId(), region.getProvider().name());
        if (credentials instanceof BasicSessionCredentials) {
            envVars.put(SystemParams.CLOUD_ACCOUNT_TOKEN_PREFIX + region.getId(),
                    ((BasicSessionCredentials) credentials).getSessionToken());
        }
        return envVars;
    }

    @Override
    public Optional<InstanceTerminationState> getInstanceTerminationState(final AwsRegion region,
                                                                          final String instanceId) {
        return ec2Helper.getInstanceStateReason(instanceId, region)
                .map(state -> InstanceTerminationState.builder()
                        .instanceId(instanceId)
                        .stateCode(state.getCode())
                        .stateMessage(state.getMessage())
                        .build());
    }

    @Override
    public void attachDisk(final AwsRegion region, final Long runId, final DiskAttachRequest request) {
        ec2Helper.createAndAttachVolume(String.valueOf(runId), request.getSize(), region,
                region.getKmsKeyArn());
    }

    @Override
    public List<InstanceDisk> loadDisks(final AwsRegion region, final Long runId) {
        return ec2Helper.loadAttachedVolumes(String.valueOf(runId), region);
    }

    @Override
    public CloudInstanceState getInstanceState(final AwsRegion region, final String nodeLabel) {
        try {
            final Instance aliveInstance = ec2Helper.getAliveInstance(nodeLabel, region);
            if (Objects.isNull(aliveInstance)) {
                return CloudInstanceState.TERMINATED;
            }
            final String instanceStateName = aliveInstance.getState().getName();
            if (InstanceStateName.Pending.toString().equals(instanceStateName)
                    || InstanceStateName.Running.toString().equals(instanceStateName)) {
                return CloudInstanceState.RUNNING;
            }
            if (InstanceStateName.Stopping.toString().equals(instanceStateName)) {
                return CloudInstanceState.STOPPING;
            }
            if (InstanceStateName.Stopped.toString().equals(instanceStateName)) {
                return CloudInstanceState.STOPPED;
            }
        } catch (AwsEc2Exception e) {
            log.error("Fail to get instance state by instance label {} for regionId {}", nodeLabel, region.getId());
        }
        return CloudInstanceState.TERMINATED;
    }

    @Override
    public InstanceDNSRecord getOrCreateInstanceDNSRecord(final AwsRegion region,
                                                          final InstanceDNSRecord record) {
        validate(record);
        log.debug("Creating DNS record {} ({})...", record.getDnsRecord(), record.getTarget());
        return route53Helper.createDNSRecord(region, getDNSHostedZoneId(region),
                getAbsoluteDNSRecord(record, getDNSHostedZoneBase(region)));
    }

    @Override
    public InstanceDNSRecord deleteInstanceDNSRecord(final AwsRegion region,
                                                     final InstanceDNSRecord record) {
        validate(record);
        log.debug("Deleting DNS record {} ({})...", record.getDnsRecord(), record.getTarget());
        return route53Helper.removeDNSRecord(region, getDNSHostedZoneId(region),
                getAbsoluteDNSRecord(record, getDNSHostedZoneBase(region)));
    }

    private void validate(final InstanceDNSRecord record) {
        Assert.notNull(record, "DNS record is missing");
        Assert.isTrue(StringUtils.isNotBlank(record.getDnsRecord()), "DNS record source is missing");
        Assert.isTrue(StringUtils.isNotBlank(record.getTarget()), "DNS record target is missing");
        Assert.notNull(record.getFormat(), "DNS record size is missing");
    }

    private static void validate(final InstanceDNSRecord record, final String base) {
        Assert.isTrue(StringUtils.contains(record.getDnsRecord(), base),
                String.format("DNS record has wrong DNS hosted zone base (%s): %s", base, record.getDnsRecord()));
    }

    private String getDNSHostedZoneId(final AwsRegion region) {
        return Optional.ofNullable(region.getDnsHostedZoneId()).map(Optional::of)
                .orElseGet(() -> Optional.of(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_ID)
                        .map(preferenceManager::getPreference))
                .orElseThrow(() -> new IllegalArgumentException("Host zone id is missing"));
    }

    private String getDNSHostedZoneBase(final AwsRegion region) {
        return Optional.ofNullable(region.getDnsHostedZoneBase()).map(Optional::of)
                .orElseGet(() -> Optional.of(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_BASE)
                        .map(preferenceManager::getPreference))
                .orElseThrow(() -> new IllegalArgumentException("Host zone base is missing"));
    }

    private InstanceDNSRecord getAbsoluteDNSRecord(final InstanceDNSRecord record, final String base) {
        switch (record.getFormat()) {
            case ABSOLUTE:
                validate(record, base);
                return record;
            case RELATIVE:
            default:
                return record.toBuilder()
                        .dnsRecord(record.getDnsRecord() + "." + base)
                        .format(InstanceDNSRecordFormat.ABSOLUTE)
                        .build();
        }
    }

    @Override
    public InstanceImage getInstanceImageDescription(final AwsRegion region, final String imageName) {
        return ec2Helper.getInstanceImageDescription(region, imageName);
    }

    @Override
    public void adjustOfferRequest(final InstanceOfferRequestVO requestVO) {
        final String volumeApiName = preferenceManager.getPreference(SystemPreferences.CLUSTER_AWS_EBS_TYPE);
        requestVO.setVolumeApiName(volumeApiName);
    }

    @Override
    public void deleteInstanceTags(final AwsRegion region, final String runId, final Set<String> tagNames) {
        ec2Helper.deleteInstanceTags(region, runId, tagNames);
    }

    private String buildNodeUpCommand(final AwsRegion region,
                                      final String nodeLabel,
                                      final RunInstance instance,
                                      final Map<String, String> labels,
                                      final Map<String, String> runtimeParameters,
                                      final Map<String, String> customTags) {
        final NodeUpCommand.NodeUpCommandBuilder commandBuilder = commandService
                .buildNodeUpCommand(nodeUpScript, region, nodeLabel, instance, getProviderName(), runtimeParameters)
                .sshKey(region.getSshKeyName())
                .customTags(customTags);

        if (StringUtils.isNotBlank(region.getKmsKeyId())) {
            commandBuilder.encryptionKey(region.getKmsKeyId());
        }
        addSpotArguments(instance, commandBuilder, region.getId());
        commandBuilder.additionalLabels(labels);
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

    private Map<String, String> buildScriptEnvVars(final AwsRegion region) {
        if (StringUtils.isBlank(region.getIamRole())) {
            return Collections.emptyMap();
        }
        final TemporaryCredentials credentials = credentialsService.generate(region);
        final HashMap<String, String> envVars = new HashMap<>();
        envVars.put(AWSUtils.AWS_ACCESS_KEY_ID_VAR, credentials.getKeyId());
        envVars.put(AWSUtils.AWS_SECRET_ACCESS_KEY_VAR, credentials.getAccessKey());
        envVars.put(AWSUtils.AWS_SESSION_TOKEN_VAR, credentials.getToken());
        envVars.put(SystemParams.GLOBAL_DISTRIBUTION_URL.name(), getGlobalDistributionUrl(region));
        return envVars;
    }

    private String getGlobalDistributionUrl(final AbstractCloudRegion region) {
        return Optional.ofNullable(region.getGlobalDistributionUrl())
                .orElseGet(() -> preferenceManager.getPreference(SystemPreferences.BASE_GLOBAL_DISTRIBUTION_URL));
    }
}
