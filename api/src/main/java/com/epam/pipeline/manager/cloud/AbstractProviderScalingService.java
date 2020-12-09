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

package com.epam.pipeline.manager.cloud;

import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.manager.CmdExecutor;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.parallel.ParallelExecutorService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public abstract class AbstractProviderScalingService<T extends AbstractCloudRegion>
    implements CloudScalingService<T> {

    protected final ClusterCommandService commandService;
    protected final CommonCloudInstanceService instanceService;
    protected final String nodeTerminateScript;
    protected final String nodeDownScript;
    protected final CmdExecutor cmdExecutor = new CmdExecutor();
    private final ParallelExecutorService executorService;
    private final String nodeReassignScript;

    public AbstractProviderScalingService(final ClusterCommandService commandService,
                                          final CommonCloudInstanceService instanceService,
                                          final ParallelExecutorService executorService,
                                          final String nodeDownScript,
                                          final String nodeReassignScript,
                                          final String nodeTerminateScript) {
        this.commandService = commandService;
        this.instanceService = instanceService;
        this.executorService=executorService;
        this.nodeDownScript = nodeDownScript;
        this.nodeReassignScript = nodeReassignScript;
        this.nodeTerminateScript = nodeTerminateScript;
    }

    @Override
    public RunInstance scaleUpNode(final T region, final Long runId, final RunInstance instance) {
        return scaleUpNode(region, instance, String.valueOf(runId), Collections.emptyMap());
    }

    @Override
    public RunInstance scaleUpPoolNode(final T region, final String nodeId, final NodePool node) {
        return scaleUpNode(region, node.toRunInstance(), nodeId, getPoolLabels(node));
    }

    @Override
    public void scaleDownNode(final T region, final Long runId) {
        scaleDownPoolNode(region, String.valueOf(runId));
    }

    @Override
    public void scaleDownPoolNode(final T region, final String nodeLabel) {
        final String command = commandService.buildNodeDownCommand(nodeDownScript, nodeLabel, getProviderName());
        final Map<String, String> envVars = buildScriptEnvVars(region);
        instanceService.runNodeDownScript(cmdExecutor, command, envVars);
    }

    @Override
    public boolean reassignNode(final T region, final Long oldId, final Long newId) {
        return reassignPoolNode(region, String.valueOf(oldId), newId);
    }

    @Override
    public boolean reassignPoolNode(final T region, final String nodeLabel, final Long newId) {
        final String command = commandService
            .buildNodeReassignCommand(nodeReassignScript, nodeLabel, String.valueOf(newId), getProviderName());
        return instanceService.runNodeReassignScript(cmdExecutor, command, nodeLabel, String.valueOf(newId),
                                                     buildScriptEnvVars(region));
    }

    @Override
    public void terminateNode(final T region, final String internalIp, final String nodeName) {
        final String command = commandService.buildTerminateNodeCommand(nodeTerminateScript, internalIp, nodeName,
                                                                        getProviderName());
        runAsync(() -> instanceService.runTerminateNodeScript(command, cmdExecutor, buildScriptEnvVars(region)));
    }

    @Override
    public LocalDateTime getNodeLaunchTime(final T region, final Long runId) {
        return instanceService.getNodeLaunchTimeFromKube(runId);
    }

    public CompletableFuture<Void> runAsync(final Runnable task) {
        if (executorService == null) {
            throw new UnsupportedOperationException(
                "Selected ProviderInstance service doesn't require asynchronous task execution.");
        }
        return CompletableFuture.runAsync(task, executorService.getExecutorService());
    }

    protected Map<String, String> buildScriptEnvVars(T region) {
        return Collections.emptyMap();
    }

    protected abstract String buildNodeUpCommand(T region, String nodeLabel, RunInstance instance,
                                                 Map<String, String> labels);


    private RunInstance scaleUpNode(final T region, final RunInstance instance, final String nodeId,
                                    final Map<String, String> labels) {
        final String command = buildNodeUpCommand(region, nodeId, instance, labels);
        final Map<String, String> envVars = buildScriptEnvVars(region);
        return instanceService.runNodeUpScript(cmdExecutor, null, instance, command, envVars);
    }

    private Map<String, String> getPoolLabels(final NodePool pool) {
        return Collections.singletonMap(KubernetesConstants.NODE_POOL_ID_LABEL, String.valueOf(pool.getId()));
    }
}
