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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class ClusterCommandService {

    private final String kubeMasterIP;
    private final String kubeToken;
    private final String kubeCertHash;
    private final String kubeNodeToken;

    public ClusterCommandService(@Value("${kube.master.ip}") final String kubeMasterIP,
                                 @Value("${kube.kubeadm.token}") final String kubeToken,
                                 @Value("${kube.kubeadm.cert.hash}") final String kubeCertHash,
                                 @Value("${kube.node.token}") final String kubeNodeToken) {
        this.kubeMasterIP = kubeMasterIP;
        this.kubeToken = kubeToken;
        this.kubeCertHash = kubeCertHash;
        this.kubeNodeToken = kubeNodeToken;
    }

    public NodeUpCommand.NodeUpCommandBuilder buildNodeUpCommand(final String nodeUpScript,
                                                                 final AbstractCloudRegion region,
                                                                 final String nodeLabel,
                                                                 final RunInstance instance,
                                                                 final String cloud,
                                                                 final Map<String, String> runtimeParameters) {
        return NodeUpCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeUpScript)
                .runId(nodeLabel)
                .instanceImage(instance.getNodeImage())
                .instanceType(instance.getNodeType())
                .instanceDisk(String.valueOf(instance.getEffectiveNodeDisk()))
                .instancePlatform(instance.getNodePlatform())
                .kubeIP(kubeMasterIP)
                .kubeToken(kubeToken)
                .kubeCertHash(kubeCertHash)
                .kubeNodeToken(kubeNodeToken)
                .cloud(cloud)
                .runtimeParameters(runtimeParameters)
                .prePulledImages(instance.getPrePulledDockerImages())
                .region(region.getRegionCode());
    }

    public String buildNodeDownCommand(final String nodeDownScript,
                                       final Long runId,
                                       final String cloud) {
        return buildNodeDownCommand(nodeDownScript, String.valueOf(runId), cloud);
    }

    public String buildNodeDownCommand(final String nodeDownScript,
                                       final String nodeLabel,
                                       final String cloud) {
        return RunIdArgCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeDownScript)
                .runId(nodeLabel)
                .cloud(cloud)
                .build()
                .getCommand();
    }

    public String buildNodeReassignCommand(final String nodeReassignScript,
                                           final Long oldId,
                                           final Long newId,
                                           final String cloud) {
        return buildNodeReassignCommand(nodeReassignScript, String.valueOf(oldId), String.valueOf(newId), cloud);
    }

    public String buildNodeReassignCommand(final String nodeReassignScript,
                                           final String oldId,
                                           final String newId,
                                           final String cloud) {
        return ReassignCommand.builder()
                .executable(AbstractClusterCommand.EXECUTABLE)
                .script(nodeReassignScript)
                .oldRunId(oldId)
                .newRunId(newId)
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
