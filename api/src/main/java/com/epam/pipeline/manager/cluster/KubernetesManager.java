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

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.MasterPodInfo;
import com.epam.pipeline.entity.cluster.NodeRegionLabels;
import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.docker.DockerRegistrySecret;
import com.epam.pipeline.entity.region.CloudProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KubernetesManager {

    private static final String SERVICE_ROLE_LABEL = "cloud-pipeline/role";
    private static final String DUMMY_EMAIL = "test@email.com";
    private static final String DOCKER_PREFIX = "docker://";
    private static final String EMPTY = "";
    private static final int NODE_READY_TIMEOUT = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 2 * 1000;
    private static final int ATTEMPTS_STATUS_NODE = 60;

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesManager.class);
    private static final String DEFAULT_SVC_SCHEME = "http";
    private static final int NODE_PULL_TIMEOUT = 200;
    private static final String NEW_LINE = "\n";

    private ObjectMapper mapper = new JsonMapper();

    @Autowired
    private MessageHelper messageHelper;

    @Value("${kube.namespace}")
    private String kubeNamespace;

    @Value("${kube.edge.ip.label}")
    private String kubeEdgeIpLabel;

    @Value("${kube.edge.port.label}")
    private String kubeEdgePortLabel;

    @Value("${kube.edge.scheme.label:cloud-pipeline/external-scheme}")
    private String kubeEdgeSchemeLabel;

    @Value("${kube.master.pod.check.url}")
    private String kubePodLeaderElectionUrl;

    @Value("${kube.current.pod.name}")
    private String kubePodName;

    public ServiceDescription getServiceByLabel(String label) {
        try (KubernetesClient client = getKubernetesClient()) {
            List<Service> items =
                    client.services().withLabel(SERVICE_ROLE_LABEL, label).list().getItems();
            if (CollectionUtils.isEmpty(items)) {
                return null;
            }
            if (items.size() > 1) {
                LOGGER.error("More than one service was found for label {}={}.", SERVICE_ROLE_LABEL, label);
            }
            Service service = items.get(0);
            return getServiceDescription(service);
        }
    }

    /**
     * Returns docker container id for specified podId and docker image name
     *
     * @param podId       id of a kubernetes pod
     * @param dockerImage docker image name from the pod
     * @return docker container id
     */
    public String getContainerIdFromKubernetesPod(String podId, String dockerImage) {
        try (KubernetesClient client = getKubernetesClient()) {
            Optional<ContainerStatus> container = client.pods()
                    .inNamespace(kubeNamespace)
                    .withName(podId)
                    .get()
                    .getStatus()
                    .getContainerStatuses()
                    .stream()
                    //find container with pipeline image
                    .filter(containerStatus -> containerStatus.getImage().contains(dockerImage))
                    .findFirst();
            if (container.isPresent()) {
                return container.get().getContainerID().replace(DOCKER_PREFIX, EMPTY);
            }
        }
        return null;
    }

    public String createDockerRegistrySecret(DockerRegistrySecret secret) {
        Secret dockerRegistrySecret;
        String encodedSecret = encodeDockerSecret(secret);
        String secretName = getValidSecretName(secret.getRegistryUrl());
        try (KubernetesClient client = getKubernetesClient()) {
            dockerRegistrySecret = client.secrets()
                    .createNew()
                    .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(kubeNamespace)
                    .endMetadata()
                    .withType("kubernetes.io/dockercfg")
                    .withData(Collections.singletonMap(".dockercfg", encodedSecret))
                    .done();
            dockerRegistrySecret.getMetadata().getName();
        }
        Assert.notNull(dockerRegistrySecret, "Failed to create a secret for docker registry");
        return dockerRegistrySecret.getMetadata().getName();
    }

    public String refreshSecret(final String secretName, final Map<String, String> data) {
        Secret secret;
        try (KubernetesClient client = getKubernetesClient()) {
            secret = client.secrets()
                    .inNamespace(kubeNamespace)
                    .withName(secretName)
                    .edit()
                    .withData(data)
                    .done();
            secret.getMetadata().getName();
        }
        Assert.notNull(secret, "Failed to refresh a secret");
        return secret.getMetadata().getName();
    }

    public String updateSecret(final String secretName, final Map<String, String> toAdd,
                               final Map<String, String> toRemove) {
        Secret secret;
        try (KubernetesClient client = getKubernetesClient()) {
            secret = client.secrets()
                    .inNamespace(kubeNamespace)
                    .withName(secretName)
                    .edit()
                    .addToData(toAdd)
                    .removeFromData(toRemove)
                    .done();
            secret.getMetadata().getName();
        }
        Assert.notNull(secret, "Failed to update a secret");
        return secret.getMetadata().getName();
    }

    public Set<String> listAllSecrets() {
        try (KubernetesClient client = getKubernetesClient()) {
            final SecretList list = client.secrets().list();
            if (Objects.isNull(list)) {
                return Collections.emptySet();
            }
            return list.getItems()
                    .stream()
                    .map(secret -> secret.getMetadata().getName())
                    .collect(Collectors.toSet());
        }
    }

    public boolean doesSecretExist(final String name) {
        try (KubernetesClient client = getKubernetesClient()) {
            return Objects.nonNull(client.secrets().inNamespace(kubeNamespace).withName(name).get());
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return false;
    }

    public void deleteSecret(String secretName) {
        if (StringUtils.isBlank(secretName)) {
            return;
        }
        try (KubernetesClient client = getKubernetesClient()) {
            client.secrets().inNamespace(kubeNamespace).withName(secretName).delete();
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public String getPodLogs(final String podId, final int limit) {
        try (KubernetesClient client = getKubernetesClient()) {
            final String tail = client.pods().inNamespace(kubeNamespace)
                    .withName(podId)
                    .tailingLines(limit + 1).getLog();
            return isLogTruncated(tail, limit)
                    ? messageHelper.getMessage(MessageConstants.LOG_WAS_TRUNCATED) + NEW_LINE + tail
                    : tail;
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    public Pod findPodById(final String podId) {
        try (KubernetesClient client = getKubernetesClient()) {
            return client.pods().inNamespace(kubeNamespace).withName(podId).get();
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    private boolean isLogTruncated(final String tail, final int limit) {
        return tail.split(NEW_LINE).length > limit;
    }

    /**
     * Updates current status with node statuses that indicate node failure.
     *
     * @param status current status
     * @param node   corresponding node
     * @return status updated with node conditions failure statuses
     */
    public String updateStatusWithNodeConditions(StringBuilder status, Node node) {
        if (node == null || node.getStatus() == null) {
            return status.toString();
        }
        List<NodeCondition> conditions = node.getStatus().getConditions();
        if (!CollectionUtils.isEmpty(conditions)) {
            for (NodeCondition condition : conditions) {
                if (isOutOfDiskFailure(condition) || isReadyFailure(condition) || isMemoryPressureFailure(condition)
                        || isDiskPressureFailure(condition) || isNetworkUnavailableFailure(condition)
                        || isConfigOKFailure(condition)) {
                    updateStatus(status, condition);
                    continue;
                }
                if (!KubernetesConstants.NODE_CONDITION_TYPES.contains(condition.getType())) {
                    updateStatus(status, condition);
                }
            }
        }
        return status.toString();
    }

    public KubernetesClient getKubernetesClient() {
        Config config = new Config();
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        return new DefaultKubernetesClient(config);
    }

    public KubernetesClient getKubernetesClient(Config config) {
        return new DefaultKubernetesClient(config);
    }

    public Optional<Node> findNodeByRunId(final String runIdLabel) {
        try (KubernetesClient client = getKubernetesClient()) {
            final NodeList list = client.nodes()
                    .withLabel(KubernetesConstants.RUN_ID_LABEL, runIdLabel).list();
            return Optional.ofNullable(list)
                    .map(l -> ListUtils.emptyIfNull(l.getItems()))
                    .flatMap(items -> items.stream().findFirst());
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Obtains a master pod name.
     *
     * @return master name or <code>null</code> if no such found
     */
    public String getMasterPodName() {
        final PodMasterStatusApi client = buildMasterStatusClient();
        try {
            final MasterPodInfo info = client.getMasterName()
                    .execute()
                    .body();
            if (info != null) {
                return info.getName();
            }
        } catch (IOException e) {
            log.warn("No leader pod for cluster found!");
        }
        return null;
    }

    /**
     * Obtains a name of pod, containing service instance.
     *
     * @return name of pod or <code>null</code> if no such name specified.
     */
    public String getCurrentPodName() {
        return kubePodName;
    }

    private PodMasterStatusApi buildMasterStatusClient() {
        return new Retrofit.Builder()
                .baseUrl(kubePodLeaderElectionUrl)
                .addConverterFactory(JacksonConverterFactory.create())
                .client(new OkHttpClient())
                .build()
                .create(PodMasterStatusApi.class);
    }

    public Optional<Node> findNodeByName(final String nodeName) {
        try (KubernetesClient client = getKubernetesClient()) {
            return Optional.ofNullable(client.nodes().withName(nodeName).get());
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
            return Optional.empty();
        }
    }

    public void deletePod(final String podId) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Boolean deleted = client
                    .pods()
                    .inNamespace(kubeNamespace)
                    .withName(podId)
                    .withGracePeriod(0)
                    .delete();
            if (Objects.isNull(deleted) || !deleted) {
                LOGGER.debug("Failed to delete pod with ID '{}'", podId);
            }
        }
    }

    public void addNodeLabel(String nodeName, String labelName, String labelValue) {
        modifyNodeLabel(nodeName, labelName, labels -> labels.put(labelName, labelValue));
    }

    public void removeNodeLabel(String nodeName, String labelName) {
        modifyNodeLabel(nodeName, labelName, labels -> labels.remove(labelName));
    }

    /**
     * Waits until node will be removed from Kubernetes cluster.
     *
     * @param nodeName
     * @param attempts
     */
    public void waitNodeDown(final String nodeName, final int attempts) {
        try (KubernetesClient client = getKubernetesClient()) {
            int tries = 0;
            while (tries < attempts) {
                final Node node = client.nodes().withName(nodeName).get();
                if (node == null) {
                    LOGGER.debug("Node {} with is down", nodeName);
                    return;
                }
                tries++;
                Thread.sleep(NODE_PULL_TIMEOUT);
            }
            throw new IllegalArgumentException(messageHelper.getMessage(
                    MessageConstants.ERROR_NODE_DOWN_TIMEOUT, nodeName));
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private void modifyNodeLabel(String nodeName, String labelName, Consumer<Map<String, String>> actionOnLabel) {
        if (StringUtils.isBlank(nodeName) || StringUtils.isBlank(labelName)) {
            return;
        }
        try (KubernetesClient client = getKubernetesClient()) {
            Node node = client.nodes().withName(nodeName).get();
            Assert.notNull(node, messageHelper.getMessage(MessageConstants.ERROR_NODE_NOT_FOUND,
                    node.getMetadata().getName()));

            Map<String, String> labels = node.getMetadata().getLabels();

            actionOnLabel.accept(labels);

            node.getMetadata().setLabels(labels);
            client.nodes().createOrReplace(node);
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void waitForNodeReady(String nodeName, String runId, String cloudRegion) throws InterruptedException {
        KubernetesClient client = getKubernetesClient();
        int attempts = ATTEMPTS_STATUS_NODE;
        while (!isReadyNode(nodeName, client)) {
            LOGGER.debug("Waiting for node {} is ready.", nodeName);
            attempts -= 1;
            Thread.sleep(NODE_READY_TIMEOUT);
            if (attempts <= 0) {
                throw new IllegalStateException(String.format(
                        "Node %s doesn't match the ready status over than %d times.", nodeName, attempts));
            }
        }
        LOGGER.debug("Labeling node with run id {}", runId);
        addNodeLabel(nodeName, KubernetesConstants.RUN_ID_LABEL, runId);
        addNodeLabel(nodeName, KubernetesConstants.CLOUD_REGION_LABEL, cloudRegion);
    }

    private boolean isReadyNode(String nodeName, KubernetesClient client) {
        Node node = client.nodes().withName(nodeName).get();
        return node != null && node.getStatus().getConditions().stream()
                .filter(nodeCondition -> nodeCondition.getType().equals(KubernetesConstants.READY))
                .allMatch(nodeCondition -> nodeCondition.getStatus().equals(KubernetesConstants.TRUE))
                && systemPodsReady(client, nodeName);
    }

    private boolean systemPodsReady(KubernetesClient client, String nodeName) {
        PodList list = getSystemPods(client, nodeName);
        if (list == null || CollectionUtils.isEmpty(list.getItems())) {
            return false;
        }
        return list.getItems().stream()
                .map(pod -> pod.getStatus().getConditions())
                .flatMap(Collection::stream)
                .filter(podCondition -> podCondition.getType().equals(KubernetesConstants.READY))
                .allMatch(podCondition -> podCondition.getStatus().equals(KubernetesConstants.TRUE));
    }

    private PodList getSystemPods(KubernetesClient client, String nodeName) {
        return client.pods().inNamespace(KubernetesConstants.SYSTEM_NAMESPACE)
                .withField(KubernetesConstants.POD_NODE_SELECTOR, nodeName).list();
    }

    public void deleteSystemPods(String nodeName) {
        try (KubernetesClient client = getKubernetesClient()) {
            client.pods().inNamespace(KubernetesConstants.SYSTEM_NAMESPACE)
                    .withField(KubernetesConstants.POD_NODE_SELECTOR, nodeName).delete();
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void deleteNode(final String nodeName) {
        final Boolean deleted = getKubernetesClient().nodes().withName(nodeName).delete();
        if (Objects.isNull(deleted) || !deleted) {
            LOGGER.debug("Failed to delete node with name '{}'", nodeName);
        }
    }

    /**
     * Retrieves kubernetes node cloud region and provider.
     *
     * @param runId Run id label to find node in kubernetes.
     * @return region labels
     */
    public NodeRegionLabels getNodeRegion(final String runId) {
        final List<Node> nodes = getKubernetesClient().nodes()
                .withLabel(KubernetesConstants.RUN_ID_LABEL, runId).list().getItems();
        if (CollectionUtils.isEmpty(nodes)) {
            throw new IllegalArgumentException(String.format("Node matching RUN ID %s was not found.", runId));
        }
        final Node node = nodes.get(0);
        final Map<String, String> labels = node.getMetadata().getLabels();
        if (MapUtils.isEmpty(labels) || (!labels.containsKey(KubernetesConstants.CLOUD_REGION_LABEL)
                && !labels.containsKey(KubernetesConstants.AWS_REGION_LABEL))) {
            throw new IllegalArgumentException(String.format("Node %s is not labeled with Cloud Region",
                    node.getMetadata().getName()));
        }

        final String regionLabel = labels.containsKey(KubernetesConstants.CLOUD_REGION_LABEL)
                ? getLabel(node, KubernetesConstants.CLOUD_REGION_LABEL)
                : getLabel(node, KubernetesConstants.AWS_REGION_LABEL);

        return new NodeRegionLabels(CloudProvider.valueOf(getLabel(node, KubernetesConstants.CLOUD_PROVIDER_LABEL)),
                regionLabel);
    }

    private String getLabel(final Node node, final String label) {
        final String value = MapUtils.emptyIfNull(node.getMetadata().getLabels()).get(label);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(String.format("Node %s is not labeled with %s",
                    node.getMetadata().getName(), label));
        }
        return value;
    }

    private String encodeDockerSecret(DockerRegistrySecret secret) {
        if (StringUtils.isEmpty(secret.getEmail())) {
            secret.setEmail(DUMMY_EMAIL);
        }
        String secretJson;
        try {
            secretJson = mapper.writeValueAsString(secret);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed docker registry secret", e);
        }
        return Base64.encodeBase64String(secretJson.getBytes());
    }

    public String getValidSecretName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-");
    }

    /**
     * Check if current host is master or not
     *
     * @return true - if a host is master or no master found, false - otherwise
     */
    public boolean isMasterHost() {
        return Optional.ofNullable(getMasterPodName())
                .map(masterName -> masterName.equals(getCurrentPodName()))
                .orElse(true);
    }

    public List<Node> getNodes(KubernetesClient client) {
        return getAvailableNodes(client).getItems();
    }

    public NodeList getAvailableNodes(KubernetesClient client) {
        return client.nodes().withLabel(KubernetesConstants.RUN_ID_LABEL)
                .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                .list();
    }

    public Set<String> getAvailableNodesIds(KubernetesClient client) {
        NodeList nodeList = getAvailableNodes(client);
        return convertKubeItemsToRunIdSet(nodeList.getItems());
    }

    public Set<String> getAllPodIds(KubernetesClient client) {
        PodList podList = getPodList(client);
        return convertKubeItemsToRunIdSet(podList.getItems());
    }

    public Set<String> convertKubeItemsToRunIdSet(List<? extends HasMetadata> items) {
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptySet();
        }
        return items.stream()
                .map(item -> item.getMetadata().getLabels().get(KubernetesConstants.RUN_ID_LABEL))
                .collect(Collectors.toSet());
    }

    public PodList getPodList(KubernetesClient client) {
        return client.pods()
                .inNamespace(kubeNamespace)
                .withLabel("type", "pipeline")
                .withLabel(KubernetesConstants.RUN_ID_LABEL).list();
    }

    public boolean isPodUnscheduled(Pod pod) {
        String phase = pod.getStatus().getPhase();
        if (KubernetesConstants.POD_SUCCEEDED_PHASE.equals(phase)
                || KubernetesConstants.POD_FAILED_PHASE.equals(phase)) {
            return false;
        }
        List<PodCondition> conditions = pod.getStatus().getConditions();
        return !CollectionUtils.isEmpty(conditions) && KubernetesConstants.POD_UNSCHEDULABLE
                .equals(conditions.get(0).getReason());
    }

    private ServiceDescription getServiceDescription(final Service service) {
        final Map<String, String> labels = service.getMetadata().getLabels();
        final String scheme = getValueFromLabelsOrDefault(labels, kubeEdgeSchemeLabel, () -> DEFAULT_SVC_SCHEME);
        final String ip = getValueFromLabelsOrDefault(labels, kubeEdgeIpLabel, () -> getExternalIp(service));
        final Integer port = getServicePort(service);
        return new ServiceDescription(scheme, ip, port);
    }

    private String getExternalIp(final Service service) {
        return ListUtils.emptyIfNull(service.getSpec().getExternalIPs())
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_KUBE_SERVICE_IP_UNDEFINED, service.getMetadata().getName())));
    }

    private Integer getServicePort(final Service service) {
        final String portLabelValue = getValueFromLabelsOrDefault(service.getMetadata().getLabels(),
            kubeEdgePortLabel, () -> {
                final Integer port = ListUtils.emptyIfNull(service.getSpec().getPorts())
                        .stream()
                        .findFirst()
                        .map(ServicePort::getPort)
                        .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                                    MessageConstants.ERROR_KUBE_SERVICE_PORT_UNDEFINED,
                                service.getMetadata().getName())));
                return String.valueOf(port);
            });
        if (NumberUtils.isDigits(portLabelValue)) {
            return Integer.parseInt(portLabelValue);
        }
        throw new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_KUBE_SERVICE_PORT_UNDEFINED, service.getMetadata().getName()));
    }

    private String getValueFromLabelsOrDefault(final Map<String, String> labels,
                                               final String labelName,
                                               final Supplier<String> defaultSupplier) {
        if (StringUtils.isBlank(labelName) || StringUtils.isBlank(MapUtils.emptyIfNull(labels).get(labelName))) {
            return defaultSupplier.get();
        }
        return labels.get(labelName);
    }

    private void updateStatus(StringBuilder status, NodeCondition condition) {
        if (!status.toString().isEmpty()) {
            status.append(", ");
        }
        status.append(condition.getType())
                .append(String.format(" (%s %s)", condition.getStatus(), condition.getReason()));
    }

    private boolean isOutOfDiskFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.OUT_OF_DISK)
                && condition.getStatus().equals(KubernetesConstants.TRUE);
    }

    private boolean isReadyFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.READY)
                && (condition.getStatus().equals(KubernetesConstants.FALSE)
                || condition.getStatus().equals(KubernetesConstants.UNKNOWN));
    }

    private boolean isMemoryPressureFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.MEMORY_PRESSURE)
                && condition.getStatus().equals(KubernetesConstants.TRUE);
    }

    private boolean isDiskPressureFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.DISK_PRESSURE)
                && condition.getStatus().equals(KubernetesConstants.TRUE);
    }

    private boolean isNetworkUnavailableFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.NETWORK_UNAVAILABLE)
                && condition.getStatus().equals(KubernetesConstants.TRUE);
    }

    private boolean isConfigOKFailure(NodeCondition condition) {
        return condition.getType().equals(KubernetesConstants.CONFIG_OK)
                && condition.getStatus().equals(KubernetesConstants.FALSE);
    }

    public boolean isNodeAvailable(final KubernetesClient client, final String nodeId) {
        return client.nodes()
                .withLabel(KubernetesConstants.RUN_ID_LABEL, nodeId)
                .withoutLabel(KubernetesConstants.PAUSED_NODE_LABEL)
                .list().getItems()
                .stream()
                .findFirst()
                .filter(this::isNodeAvailable)
                .isPresent();
    }

    public boolean isNodeAvailable(final Node node) {
        if (node == null) {
            return false;
        }
        List<NodeCondition> conditions = node.getStatus().getConditions();
        if (CollectionUtils.isEmpty(conditions)) {
            return true;
        }
        String lastReason = conditions.get(0).getReason();
        for (String reason : KubernetesConstants.NODE_OUT_OF_ORDER_REASONS) {
            if (lastReason.contains(reason)) {
                log.debug("Node is out of order: {}", conditions);
                return false;
            }
        }
        return true;
    }

    public boolean isNodeUnavailable(final Node node) {
        return !isNodeAvailable(node);
    }
}