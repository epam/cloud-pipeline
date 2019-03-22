/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.CmdExecutionException;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.AwsRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.security.UserContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClusterManagerImpl implements ClusterManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterManagerImpl.class);
    private static final String EXECUTABLE = "python";
    private static final String RUN_ID_PARAMETER = "--run_id";
    private static final String AWS_REGION_PARAMETER = "--region_id";
    private static final String AWS_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int TIME_DELIMITER = 60;
    private static final int TIME_TO_SHUT_DOWN_NODE = 1;
    private static final String MANUAL = "manual";
    private static final String ON_DEMAND = "on_demand";

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern(AWS_DATE_FORMAT);

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private Environment environment;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private AwsRegionManager awsRegionManager;

    @Autowired
    private EC2Helper ec2Helper;

    private String nodeUpScript;
    private String nodeDownScript;
    private String nodeLaunchTimeScript;
    private String nodeReassignScript;
    private String nodeDescribeScript;
    private String kubeMasterIP;
    private String kubeadmToken;

    @PostConstruct
    public void init() {
        if (preferenceManager.getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING)) {
            nodeUpScript = getProperty("cluster.nodeup.script");
            nodeDownScript = getProperty("cluster.nodedown.script");
            nodeLaunchTimeScript = getProperty("cluster.launch.time.script");
            nodeReassignScript = getProperty("cluster.reassign.script");
            nodeDescribeScript = getProperty("cluster.describe.script");
            kubeMasterIP = getProperty("kube.master.ip");
            kubeadmToken = getProperty("kube.kubeadm.token");
        }
    }

    private String getProperty(String propertyKey) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            throw new IllegalArgumentException("Could not get property:" + propertyKey);
        }

        return property;
    }

    @Autowired
    private InstanceOfferManager instanceOfferManager;

    private CmdExecutor cmdExecutor = new CmdExecutor();

    @Override
    public RunInstance scaleUp(String runId, RunInstance instance) {
        String command = buildNodeUpCommand(runId, instance);
        LOGGER.debug("Scaling cluster up. Command: {}.", command);
        String output = cmdExecutor.executeCommandWithEnvVars(command, buildNodeUpEnvVars(runId));
        LOGGER.debug("Scale up output: {}.", output);
        readInstanceId(instance, output);
        return instance;
    }

    @Override
    public void scaleDown(String runId) {
        String command = buildRunIdArgCommand(runId, nodeDownScript);
        LOGGER.debug("Scaling cluster down. Command: {}.", command);
        executeCmd(command, null);
    }

    @Override
    public boolean isNodeExpired(String runId) {
        Integer keepAliveMinutes = preferenceManager.getPreference(SystemPreferences.CLUSTER_KEEP_ALIVE_MINUTES);

        if (keepAliveMinutes == null) {
            return true;
        }
        String command = buildRunIdArgCommand(runId, nodeLaunchTimeScript);
        try {
            LOGGER.debug("Getting node launch time. Command: {}.", command);
            String result = cmdExecutor.executeCommand(command).trim();
            LOGGER.debug("Node {} launch time {}.", runId, result);
            LocalDateTime launchTime = LocalDateTime.parse(result, formatter);
            LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
            long aliveTime = Duration.between(launchTime, now).getSeconds() / TIME_DELIMITER;
            LOGGER.debug("Node {} is alive for {} minutes.", runId, aliveTime);
            long minutesToWholeHour = aliveTime % TIME_DELIMITER;
            long minutesLeft = TIME_DELIMITER - minutesToWholeHour;
            LOGGER.debug("Node {} has {} minutes left until next hour.", runId, minutesLeft);
            return minutesLeft <= keepAliveMinutes && minutesLeft > TIME_TO_SHUT_DOWN_NODE;
        } catch (DateTimeParseException | CmdExecutionException e) {
            LOGGER.error(e.getMessage(), e);
            return true;
        }
    }

    @Override
    public boolean reassignNode(String oldId, String newId) {
        String command = buildNodeReassignCommand(oldId, newId);
        LOGGER.debug("Reusing Node with previous ID {} for rud ID {}. Command {}.", oldId, newId, command);
        try {
            cmdExecutor.executeCommand(command);
            return true;
        } catch (CmdExecutionException e) {
            LOGGER.error("Failed to reassign node from {} to {}", oldId, newId);
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean requirementsMatch(RunInstance instanceOld, RunInstance instanceNew) {
        if (instanceOld == null || instanceNew == null) {
            return false;
        }
        return instanceOld.requirementsMatch(instanceNew);
    }

    @Override
    public void scaleUpFreeNode(String nodeId) {
        String command = buildNodeUpDefaultCommand(nodeId);
        LOGGER.debug("Creating default free node. Command: {}.", command);
        executeCmd(command, buildNodeUpEnvVars(nodeId));
    }

    @Override
    public RunInstance getDefaultInstance() {
        RunInstance instance = new RunInstance();
        instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        instance.setEffectiveNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        instance.setNodeImage(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_IMAGE));
        instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        instance.setAwsRegionId(awsRegionManager.loadDefaultRegion().getAwsRegionName());
        return instance;
    }

    @Override
    public RunInstance configurationToInstance(PipelineConfiguration configuration) {
        RunInstance instance = new RunInstance();
        if (configuration.getInstanceType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        } else {
            instance.setNodeType(configuration.getInstanceType());
        }
        if (configuration.getInstanceDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        } else {
            instance.setNodeDisk(Integer.parseInt(configuration.getInstanceDisk()));
        }
        instance.setEffectiveNodeDisk(instance.getNodeDisk());
        if (configuration.getInstanceImage() == null) {
            instance.setNodeImage(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_IMAGE));
        } else {
            instance.setNodeImage(configuration.getInstanceImage());
        }
        instance.setAwsRegionId(Optional.ofNullable(configuration.getAwsRegionId())
                .map(regionId -> awsRegionManager.load(regionId))
                .orElse(awsRegionManager.loadDefaultRegion())
                .getAwsRegionName());
        return instance;
    }

    @Override
    public RunInstance fillInstance(RunInstance instance) {
        if (instance.getNodeType() == null) {
            instance.setNodeType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        }
        if (instance.getNodeDisk() == null) {
            instance.setNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
            instance.setEffectiveNodeDisk(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD));
        }
        if (instance.getNodeImage() == null) {
            instance.setNodeImage(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_IMAGE));
        }
        if (instance.getAwsRegionId() == null) {
            instance.setAwsRegionId(awsRegionManager.loadDefaultRegion().getAwsRegionName());
        }
        return instance;
    }

    @Override
    public void stopInstance(String instanceId, String awsRegion) {
        LOGGER.debug("Stopping instance with ID {}.", instanceId);
        ec2Helper.stopInstance(instanceId, awsRegion);
        LOGGER.debug("Instance with ID {} was stopped.", instanceId);
    }

    @Override
    public void startInstance(String instanceId, String awsRegion) {
        LOGGER.debug("Starting instance with ID {}.", instanceId);
        ec2Helper.startInstance(instanceId, awsRegion);
        LOGGER.debug("Instance with ID {} was started.", instanceId);
    }

    @Override
    public RunInstance describeInstance(String runId, RunInstance instance) {
        String command = buildNodeDescribeCommand(runId, instance.getAwsRegionId());
        LOGGER.debug("Getting instance description. Command: {}.", command);
        try {
            String output = cmdExecutor.executeCommand(command, true);
            readInstanceId(instance, output);
        } catch (CmdExecutionException e) {
            LOGGER.trace(e.getMessage(), e);
            return null;
        }
        return instance;
    }

    private String buildNodeReassignCommand(String oldId, String newId) {
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(nodeReassignScript);
        commands.add("--old_id");
        commands.add(oldId);
        commands.add("--new_id");
        commands.add(newId);
        return commands.stream().collect(Collectors.joining(" "));
    }

    private String buildRunIdArgCommand(String runId, String script) {
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(script);
        commands.add(RUN_ID_PARAMETER);
        commands.add(runId);
        return commands.stream().collect(Collectors.joining(" "));
    }

    private String buildNodeDescribeCommand(String runId, String regionId) {
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(nodeDescribeScript);
        commands.add(RUN_ID_PARAMETER);
        commands.add(runId);
        commands.add(AWS_REGION_PARAMETER);
        commands.add(regionId);
        return commands.stream().collect(Collectors.joining(" "));
    }

    private String buildNodeUpCommand(String runId, RunInstance instance) {
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(nodeUpScript);
        commands.add(RUN_ID_PARAMETER);
        commands.add(runId);
        commands.add("--ins_key");
        commands.add(preferenceManager.getPreference(SystemPreferences.CLUSTER_SSH_KEY_NAME));
        commands.add("--ins_img");
        commands.add(instance.getNodeImage());
        commands.add("--ins_type");
        commands.add(instance.getNodeType());
        commands.add("--ins_hdd");
        commands.add(instance.getEffectiveNodeDisk().toString());
        commands.add("--kube_ip");
        commands.add(kubeMasterIP);
        commands.add("--kubeadm_token");
        commands.add(kubeadmToken);
        commands.add(AWS_REGION_PARAMETER);
        commands.add(instance.getAwsRegionId());

        String kmsDataEncryptionKeyId = awsRegionManager.loadByAwsRegionName(instance.getAwsRegionId())
                .getKmsKeyId();
        if (StringUtils.isNotBlank(kmsDataEncryptionKeyId)) {
            commands.add("--kms_encyr_key_id");
            commands.add(kmsDataEncryptionKeyId);
        }

        addSpotArguments(instance, commands, instance.getAwsRegionId());
        return commands.stream().collect(Collectors.joining(" "));
    }

    private void addSpotArguments(RunInstance instance, List<String> commands, String awsRegionId) {
        Boolean instanceSpotFlag = instance.getSpot();
        boolean isSpot = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT);

        final boolean useSpot = (instanceSpotFlag == null && isSpot)
                || (instanceSpotFlag != null && instanceSpotFlag);
        if (useSpot) {
            Double bidPrice = customizeSpotArguments(instance.getNodeType(), awsRegionId);
            commands.add("--is_spot");
            commands.add("True");
            commands.add("--bid_price");
            commands.add(bidPrice == null ? "" : String.valueOf(bidPrice));
        }
    }

    private Double customizeSpotArguments(String instanceType, String awsRegionId) {
        String spotAllocStrategy = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT_ALLOC_STRATEGY);
        Double bidPrice = preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT_BID_PRICE);
        switch (spotAllocStrategy) {
            case MANUAL:
                if (bidPrice == null) {
                    LOGGER.error("Spot price must be specified in \'" + MANUAL + "\' case.");
                }
                return bidPrice;
            case ON_DEMAND:
                return instanceOfferManager.getPricePerHourForInstance(instanceType, awsRegionId);
            default:
                LOGGER.error("Argument spot_alloc_strategy must have \'" + MANUAL + "\' or \'"
                        + ON_DEMAND + "\' value.");
                return bidPrice;
        }
    }

    private String buildNodeUpDefaultCommand(String nodeId) {
        AwsRegion region = awsRegionManager.loadDefaultRegion();
        List<String> commands = new ArrayList<>();
        commands.add(EXECUTABLE);
        commands.add(nodeUpScript);
        commands.add(RUN_ID_PARAMETER);
        commands.add(nodeId);
        commands.add("--ins_key");
        commands.add(preferenceManager.getPreference(SystemPreferences.CLUSTER_SSH_KEY_NAME));
        commands.add("--ins_img");
        commands.add(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_IMAGE));
        commands.add("--ins_type");
        commands.add(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE));
        commands.add("--ins_hdd");
        commands.add(String.valueOf(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD)));

        if (preferenceManager.getPreference(SystemPreferences.CLUSTER_SPOT)) {
            commands.add("--is_spot");
            commands.add("True");
            commands.add("--bid_price");
            Double bidPrice = customizeSpotArguments(preferenceManager.getPreference(
                    SystemPreferences.CLUSTER_INSTANCE_TYPE), region.getAwsRegionName());
            commands.add(bidPrice == null ? "" : String.valueOf(bidPrice));
        }
        commands.add("--region_id");
        commands.add(region.getAwsRegionName());
        return commands.stream().collect(Collectors.joining(" "));
    }

    private void readInstanceId(RunInstance instance, String output) {
        String[] node = output.split("\\s+");
        if (node.length == 3) {
            instance.setNodeId(node[0]);
            instance.setNodeIP(node[1]);
            instance.setNodeName(node[2]);
        }
    }

    private void executeCmd(String command, Map<String, String> envVars) {
        try {
            cmdExecutor.executeCommandWithEnvVars(command, envVars);
        } catch (CmdExecutionException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private String getApiTokenForRun(String runId) {
        PipelineRun run = pipelineRunManager.loadPipelineRun(Long.valueOf(runId));
        UserContext owner = Optional.ofNullable(authManager.getUserContext())
                .orElse(userManager.loadUserContext(run.getOwner()));
        return authManager.issueToken(owner, null).getToken();
    }

    private Map<String, String> buildNodeUpEnvVars(String id) {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("API", preferenceManager.getPreference(SystemPreferences.BASE_API_HOST));
        envVars.put("API_TOKEN", getApiTokenForRun(id));
        return envVars;
    }
}
