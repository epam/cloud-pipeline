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

package com.epam.pipeline.manager.execution;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.internal.PodOperationsImpl;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import okhttp3.OkHttpClient;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PipelineExecutor {
    @Autowired
    private PreferenceManager preferenceManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineExecutor.class);
    private static final String NGINX_ENDPOINT = "nginx";

    @Value("${kube.namespace}")
    private String kubeNamespace;

    private static final long KUBE_TERMINATION_PERIOD = 30L;

    public void launchRootPod(String command, PipelineRun run, List<EnvVar> envVars, List<String> endpoints,
                              String pipelineId, String nodeIdLabel, String secretName, String clusterId) {
        launchRootPod(command, run, envVars, endpoints, pipelineId, nodeIdLabel, secretName, clusterId, true);
    }

    public void launchRootPod(String command, PipelineRun run, List<EnvVar> envVars, List<String> endpoints,
            String pipelineId, String nodeIdLabel, String secretName, String clusterId, boolean pullImage) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            Map<String, String> labels = new HashMap<>();
            labels.put("spawned_by", "pipeline-api");
            labels.put("pipeline_id", pipelineId);
            addWorkerLabel(clusterId, labels, run);
            LOGGER.debug("Root pipeline task ID: {}", run.getPodId());
            Map<String, String> nodeSelector = new HashMap<>();

            if (preferenceManager.getPreference(SystemPreferences.CLUSTER_ENABLE_AUTOSCALING)) {
                String runIdLabel = String.valueOf(run.getId());
                nodeSelector.put("runid", nodeIdLabel);
                // id pod ip == pipeline id we have a root pod, otherwise we prefer to skip pod in autoscaler
                if (run.getPodId().equals(pipelineId) && nodeIdLabel.equals(runIdLabel)) {
                    labels.put("type", "pipeline");
                }
                labels.put("runid", runIdLabel);
            } else {
                nodeSelector.put("skill", "luigi");
            }

            labels.putAll(getServiceLabels(endpoints));

            OkHttpClient httpClient = HttpClientUtils.createHttpClient(client.getConfiguration());
            ObjectMeta metadata = getObjectMeta(run, labels);
            PodSpec spec = getPodSpec(run, envVars, secretName, nodeSelector, run.getDockerImage(), command, pullImage);
            Pod pod = new Pod("v1", "Pod", metadata, spec, null);
            Pod created = new PodOperationsImpl(httpClient, client.getConfiguration(), kubeNamespace).create(pod);
            LOGGER.debug("Created POD: {}", created.toString());
        }
    }

    private void addWorkerLabel(final String clusterId, final Map<String, String> labels, final PipelineRun run) {
        final String clusterLabel = StringUtils.defaultIfBlank(clusterId,
                Optional.ofNullable(run.getParentRunId()).map(String::valueOf).orElse(StringUtils.EMPTY));
        if (StringUtils.isNotBlank(clusterLabel)) {
            labels.put(KubernetesConstants.POD_WORKER_NODE_LABEL, clusterLabel);
        }
    }

    private PodSpec getPodSpec(PipelineRun run, List<EnvVar> envVars, String secretName,
                               Map<String, String> nodeSelector, String dockerImage,
                               String command, boolean pullImage) {
        PodSpec spec = new PodSpec();
        spec.setRestartPolicy("Never");
        spec.setTerminationGracePeriodSeconds(KUBE_TERMINATION_PERIOD);
        spec.setDnsPolicy("ClusterFirst");
        spec.setNodeSelector(nodeSelector);
        if (run.getTimeout() != null && run.getTimeout() > 0) {
            spec.setActiveDeadlineSeconds(run.getTimeout() * Constants.SECONDS_IN_MINUTE);
        }
        if (!StringUtils.isEmpty(secretName)) {
            spec.setImagePullSecrets(Collections.singletonList(new LocalObjectReference(secretName)));
        }
        spec.setVolumes(Arrays.asList(
                createVolume("ref-data", "/ebs/reference"),
                createVolume("runs-data", "/ebs/runs"),
                createEmptyVolume("dshm", "Memory")));
        spec.setContainers(Collections.singletonList(getContainer(envVars, dockerImage, command, pullImage)));
        return spec;
    }

    private Container getContainer(List<EnvVar> envVars, String dockerImage, String command, boolean pullImage) {
        Container container = new Container();
        container.setName("pipeline");
        SecurityContext securityContext = new SecurityContext();
        securityContext.setPrivileged(true);
        container.setSecurityContext(securityContext);
        container.setEnv(envVars);
        container.setImage(dockerImage);
        container.setCommand(Collections.singletonList("/bin/bash"));
        if (!StringUtils.isEmpty(command)) {
            container.setArgs(Arrays.asList("-c", command));
        }
        container.setTerminationMessagePath("/dev/termination-log");
        container.setImagePullPolicy(pullImage ? "Always" : "Never");
        container.setVolumeMounts(Arrays.asList(
                getVolumeMount("ref-data", "/common"),
                getVolumeMount("runs-data", "/runs"),
                getVolumeMount("dshm", "/dev/shm")));
        return container;
    }

    private VolumeMount getVolumeMount(String name, String path) {
        VolumeMount mount = new VolumeMount();
        mount.setName(name);
        mount.setMountPath(path);
        return mount;
    }

    private ObjectMeta getObjectMeta(PipelineRun run, Map<String, String> labels) {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(run.getPodId());
        metadata.setLabels(labels);
        return metadata;
    }

    private Volume createVolume(String name, String hostPath) {
        Volume volume = new Volume();
        volume.setName(name);
        volume.setHostPath(new HostPathVolumeSource(hostPath));
        return volume;
    }

    private Volume createEmptyVolume(String name, String medium) {
        Volume volume = new Volume();
        EmptyDirVolumeSource emptyDir = new EmptyDirVolumeSource();
        emptyDir.setMedium(medium);
        volume.setEmptyDir(emptyDir);
        volume.setName(name);
        return volume;
    }

    private Map<String, String> getServiceLabels(List<String> endpointsString) {
        Map<String, String> labels = new HashMap<>();
        if (CollectionUtils.isEmpty(endpointsString)) {
            return labels;
        }
        if (endpointsString.stream()
                .anyMatch(s -> !StringUtils.isEmpty(s) && s.contains(NGINX_ENDPOINT))) {
            labels.put("job-type", "Service");
        }
        return labels;
    }

}
