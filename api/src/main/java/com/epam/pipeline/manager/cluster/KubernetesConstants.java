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

package com.epam.pipeline.manager.cluster;

import com.epam.pipeline.entity.cluster.DockerMount;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Mariia_Zueva on 5/2/2017.
 */
public final class KubernetesConstants {

    public static final String RUN_ID_LABEL = "runid";
    public static final String TYPE_LABEL = "type";
    public static final String PIPELINE_TYPE = "pipeline";
    public static final String NODE_POOL_ID_LABEL = "pool_id";
    public static final String CLOUD_REGION_LABEL = "cloud_region";
    public static final String AWS_REGION_LABEL = "aws_region";
    public static final String CLOUD_PROVIDER_LABEL = "cloud_provider";
    public static final String POD_WORKER_NODE_LABEL = "cluster_id";
    public static final String PAUSED_NODE_LABEL = "Paused";
    public static final String UNAVAILABLE_NODE_LABEL = "Unavailable";

    public static final String CP_CAP_DIND_NATIVE = "CP_CAP_DIND_NATIVE";
    public static final String CP_CAP_SYSTEMD_CONTAINER = "CP_CAP_SYSTEMD_CONTAINER";

    public static final String KUBE_NAME_REGEXP = "[^a-z0-9\\-]+";
    public static final String KUBE_NAME_FULL_REGEXP = "[^a-zA-Z0-9\\-._]+";

    public static final String POD_SUCCEEDED_PHASE = "Succeeded";
    public static final String POD_FAILED_PHASE = "Failed";
    public static final String NODE_LOST = "NodeLost";
    public static final String POD_UNSCHEDULABLE = "Unschedulable";
    public static final String POD_RUNNING_PHASE = "Running";
    public static final String POD_NODE_SELECTOR_OPERATOR_IN = "In";

    public static final String WINDOWS = "windows";

    public static final String CP_LABEL_PREFIX = "cloud-pipeline/";
    public static final String TRUE_LABEL_VALUE = "true";
    public static final String KUBERNETES_APP_LABEL = "k8s-app";
    public static final String KUBE_DNS_APP = "kube-dns";
    public static final String HYPHEN = "-";
    public static final String KUBE_UNREACHABLE_NODE_LABEL = "node.kubernetes.io/unreachable";
    public static final String KUBE_NOT_READY_NODE_LABEL = "node.kubernetes.io/not-ready";

    protected static final String SYSTEM_NAMESPACE = "kube-system";
    protected static final String POD_NODE_SELECTOR = "spec.nodeName";

    // node condition types
    protected static final String OUT_OF_DISK = "OutOfDisk";
    protected static final String READY = "Ready";
    protected static final String MEMORY_PRESSURE = "MemoryPressure";
    protected static final String DISK_PRESSURE = "DiskPressure";
    protected static final String NETWORK_UNAVAILABLE = "NetworkUnavailable";
    protected static final String CONFIG_OK = "ConfigOK";
    protected static final String PID_PRESSURE = "PIDPressure";

    // node condition statuses
    protected static final String TRUE = "True";
    protected static final String FALSE = "False";
    protected static final String UNKNOWN = "Unknown";

    protected static final Set<String> NODE_CONDITION_TYPES =
            Stream.of(OUT_OF_DISK, READY, MEMORY_PRESSURE, DISK_PRESSURE,
                    NETWORK_UNAVAILABLE, CONFIG_OK, PID_PRESSURE)
                    .collect(Collectors.toSet());

    protected static final String TCP = "TCP";

    public static final Set<String> NODE_OUT_OF_ORDER_REASONS = new HashSet<>();

    static {
        NODE_OUT_OF_ORDER_REASONS.add("KubeletOutOfDisk");
        NODE_OUT_OF_ORDER_REASONS.add("NodeStatusUnknown");
    }

    public static final List<DockerMount> DEFAULT_DOCKER_IN_DOCKER_MOUNTS = new ArrayList<>();

    static {
        DEFAULT_DOCKER_IN_DOCKER_MOUNTS.add(DockerMount.builder()
                .name("docker-sock")
                .hostPath("/var/run/docker.sock")
                .mountPath("/var/run/docker.sock")
                .build());
        DEFAULT_DOCKER_IN_DOCKER_MOUNTS.add(DockerMount.builder()
                .name("docker-bin")
                .hostPath("/bin/docker")
                .mountPath("/usr/bin/docker")
                .build());
    }

    public static final DateTimeFormatter KUBE_DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    public static final DateTimeFormatter KUBE_LABEL_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss.SSSSSS");

    private KubernetesConstants() {
        //no op
    }
}
