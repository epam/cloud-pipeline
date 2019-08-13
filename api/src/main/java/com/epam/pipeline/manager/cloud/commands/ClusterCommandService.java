/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cloud.commands;

import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ClusterCommandService {

    private final PreferenceManager preferenceManager;
    private final String kubeMasterIP;
    private final String kubeToken;

    public ClusterCommandService(final PreferenceManager preferenceManager,
                                 @Value("${kube.master.ip}") final String kubeMasterIP,
                                 @Value("${kube.kubeadm.token}") final String kubeToken) {
        this.preferenceManager = preferenceManager;
        this.kubeMasterIP = kubeMasterIP;
        this.kubeToken = kubeToken;
    }

    public NodeUpCommand.NodeUpCommandBuilder buildNodeUpCommand(final String nodeUpScript,
                                                                 final AbstractCloudRegion region,
                                                                 final Long runId,
                                                                 final RunInstance instance,
                                                                 final String cloud) {
        return NodeUpCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeUpScript)
                .runId(String.valueOf(runId))
                .instanceImage(instance.getNodeImage())
                .instanceType(instance.getNodeType())
                .instanceDisk(String.valueOf(instance.getEffectiveNodeDisk()))
                .kubeIP(kubeMasterIP)
                .kubeToken(kubeToken)
                .cloud(cloud)
                .region(region.getRegionCode());
    }

    public NodeUpCommand.NodeUpCommandBuilder buildDefaultNodeUpCommand(final String nodeUpScript,
                                                                        final AbstractCloudRegion region,
                                                                        final String nodeId,
                                                                        final String cloud) {
        return NodeUpCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeUpScript)
                .runId(nodeId)
                .instanceType(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_TYPE))
                .instanceDisk(String.valueOf(preferenceManager.getPreference(SystemPreferences.CLUSTER_INSTANCE_HDD)))
                .kubeIP(kubeMasterIP)
                .kubeToken(kubeToken)
                .cloud(cloud)
                .region(region.getRegionCode());
    }

    public String buildNodeDownCommand(final String nodeDownScript,
                                       final Long runId,
                                       final String cloud) {
        return RunIdArgCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeDownScript)
                .runId(String.valueOf(runId))
                .cloud(cloud)
                .build()
                .getCommand();
    }

    public String buildNodeReassignCommand(final String nodeReassignScript,
                                           final Long oldId,
                                           final Long newId,
                                           final String cloud) {
        return ReassignCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeReassignScript)
                .oldRunId(String.valueOf(oldId))
                .newRunId(String.valueOf(newId))
                .cloud(cloud)
                .build()
                .getCommand();
    }

    public String buildTerminateNodeCommand(final String nodeTerminateScript,
                                            final String internalIp,
                                            final String nodeName,
                                            final String cloud) {
        return TerminateNodeCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeTerminateScript)
                .internalIp(internalIp)
                .nodeName(nodeName)
                .cloud(cloud)
                .build()
                .getCommand();
    }
}
