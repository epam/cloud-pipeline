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
import com.epam.pipeline.entity.docker.DockerRegistrySecret;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.NodeStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.epam.pipeline.manager.cluster.KubernetesConstants.HYPHEN;

@Slf4j
@Component
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class KubernetesManager {


    private static final String SERVICE_ROLE_LABEL = KubernetesConstants.CP_LABEL_PREFIX + "role";
    private static final String DUMMY_EMAIL = "test@email.com";
    private static final String DOCKER_PREFIX = "docker://";
    private static final String EMPTY = "";
    private static final int MILLIS_TO_SECONDS = 1000;
    private static final int CONNECTION_TIMEOUT_MS = 2 * MILLIS_TO_SECONDS;

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesManager.class);
    private static final int NODE_PULL_TIMEOUT = 200;
    private static final String NEW_LINE = "\n";

    private ObjectMapper mapper = new JsonMapper();

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private PreferenceManager preferenceManager;

    @Autowired
    private KubernetesDeploymentAPIClient deploymentAPIClient;

    @Value("${kube.namespace}")
    private String kubeNamespace;

    @Value("${kube.master.pod.check.url}")
    private String kubePodLeaderElectionUrl;

    @Value("${kube.current.pod.name}")
    private String kubePodName;

    @Value("${kube.default.service.target.port:1000}")
    private Integer defaultKubeServiceTargetPort;

    @Value("${kube.deployment.refresh.timeout:3}")
    private Integer deploymentRefreshTimeoutSec;

    @Value("${kube.deployment.refresh.retries:15}")
    private Integer deploymentRefreshRetries;

    @Value("${kube.annotation.value.length.limit:254}")
    private Integer kubeAnnotationLengthLimit;

    @Value("${kube.label.value.length.limit:63}")
    private Integer kubeLabelValueSizeLimit;

    @Value("${kube.label.long.value.suffix.length:5}")
    private Integer kubeLabelLongValueRandomSuffixSize;

    @Value("${kube.label.long.value.reducing.length:12}")
    private Integer kubeLabelLongValueReducingSize;

    public List<Service> getServicesByLabel(final String label) {
        return getServicesByLabel(SERVICE_ROLE_LABEL, label);
    }

    public List<Service> getCloudPipelineServiceInstances(final String serviceName) {
        return getServicesByLabel(KubernetesConstants.CP_LABEL_PREFIX + serviceName,
                                  KubernetesConstants.TRUE_LABEL_VALUE);
    }

    public List<Service> getServicesByLabel(final String labelName, final String labelValue) {
        try (KubernetesClient client = getKubernetesClient()) {
            return findServicesByLabel(client, labelName, labelValue);
        }
    }

    public List<Service> getServicesByLabels(final Map<String, String> labels, final String serviceNameLabel) {
        try (KubernetesClient client = getKubernetesClient()) {
            labels.put(SERVICE_ROLE_LABEL, serviceNameLabel);
            return findServicesByLabels(client, labels);
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

    public boolean refreshDeployment(final String deploymentName, final Map<String, String> labelSelector) {
        return refreshDeployment(kubeNamespace, deploymentName, labelSelector);
    }

    public boolean refreshDeployment(final String namespace, final String deploymentName,
                                     final Map<String, String> labelSelector) {
        Assert.isTrue(StringUtils.isNotBlank(namespace),
                      messageHelper.getMessage(MessageConstants.ERROR_KUBE_NAMESPACE_NOT_SPECIFIED));
        try {
            deploymentAPIClient.updateDeployment(namespace, deploymentName);
            try (KubernetesClient client = getKubernetesClient()) {
                return waitForPodsStartup(client, namespace, labelSelector,
                                          deploymentRefreshRetries, deploymentRefreshTimeoutSec);
            }
        } catch (RuntimeException e) {
            log.warn(messageHelper.getMessage(MessageConstants.ERROR_KUBE_DEPLOYMENT_REFRESH_FAILED,
                                              namespace, deploymentName, e.getMessage()));
            return false;
        }
    }

    private boolean waitForPodsStartup(final KubernetesClient client, final String namespace,
                                       final Map<String, String> labelSelector, final int retries,
                                       final int retryTimeoutSeconds) {
        long attempts = retries;
        while (!podsAreReady(client, namespace, labelSelector)) {
            LOGGER.debug("Waiting for pods in [{},{}] to be ready.", namespace, labelSelector);
            attempts -= 1;
            try {
                Thread.sleep(retryTimeoutSeconds * MILLIS_TO_SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interruption occurred during [{},{}] refreshing, exiting...", namespace, labelSelector);
                return false;
            }
            if (attempts <= 0) {
                log.error("Unable to boot up pods in [{},{}] in {} reties", namespace, labelSelector, retries);
                return false;
            }
        }
        return true;
    }

    private boolean podsAreReady(final KubernetesClient client, final String namespace,
                                 final Map<String, String> labelSelector) {
        final PodList allServicePods = client.pods()
            .inNamespace(namespace)
            .withLabels(labelSelector)
            .list();
        return allPodsAreReady(allServicePods);
    }

    public boolean updateAnnotationsOfExistingService(final String serviceName,
                                                      final Map<String, String> annotationUpdate) {
        try (KubernetesClient client = getKubernetesClient()) {
            client.services()
                .inNamespace(kubeNamespace)
                .withName(serviceName)
                .edit()
                .editMetadata()
                .addToAnnotations(annotationUpdate)
                .endMetadata()
                .done();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean removeAnnotationsFromExistingService(final String serviceName, final Set<String> annotations) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Map<String, String> correspondingAnnotations = client.services()
                .inNamespace(kubeNamespace)
                .withName(serviceName)
                .get()
                .getMetadata()
                .getAnnotations()
                .entrySet()
                .stream()
                .filter(e -> annotations.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (MapUtils.isNotEmpty(correspondingAnnotations)) {
                client.services()
                    .inNamespace(kubeNamespace)
                    .withName(serviceName)
                    .edit()
                    .editMetadata()
                    .removeFromAnnotations(correspondingAnnotations)
                    .endMetadata()
                    .done();
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Optional<ServicePort> addPortToExistingService(final String serviceName,
                                                          final Integer externalPort, final Integer internalPort,
                                                          final String protocol) {
        try (KubernetesClient client = getKubernetesClient()) {
            final ServicePort newPortSpec = getTcpPortSpec(getServicePortName(serviceName, externalPort),
                                                           externalPort, internalPort, protocol);
            client.services()
                .inNamespace(kubeNamespace)
                .withName(serviceName)
                .edit()
                    .editSpec()
                        .addToPorts(newPortSpec)
                    .endSpec()
                .done();
            return Optional.of(newPortSpec);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public String buildProxyServiceName(final String prefix, final String externalName) {
        final String fullName = prefix + HYPHEN + externalName.replaceAll("\\.", HYPHEN);
        if (fullName.length() < kubeLabelValueSizeLimit) {
            return fullName;
        }
        final String randomSuffix = RandomStringUtils.randomAlphanumeric(kubeLabelLongValueRandomSuffixSize)
            .toLowerCase();
        return fullName.substring(0, kubeLabelValueSizeLimit - kubeLabelLongValueReducingSize) + HYPHEN + randomSuffix;
    }

    public String getServicePortName(final String serviceName, final Integer externalPort) {
        final String portSuffix = externalPort.toString();
        final String fullName = serviceName + HYPHEN + portSuffix;
        if (fullName.length() < kubeLabelValueSizeLimit) {
            return fullName;
        }
        return fullName.substring(0, kubeLabelValueSizeLimit - portSuffix.length() - 1) + HYPHEN + portSuffix;
    }

    public boolean setPortsToService(final String serviceName, final List<ServicePort> portsUpdate) {
        try (KubernetesClient client = getKubernetesClient()) {
            client.services()
                .inNamespace(kubeNamespace)
                .withName(serviceName)
                .edit()
                    .editSpec()
                        .withPorts(portsUpdate)
                    .endSpec()
                .done();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private ServicePort getTcpPortSpec(final String portName, final Integer externalPort, final Integer internalPort,
                                       final String protocol) {
        final ServicePort newServicePort = new ServicePort();
        newServicePort.setProtocol(protocol);
        newServicePort.setName(portName);
        Optional.ofNullable(externalPort).ifPresent(newServicePort::setPort);
        Optional.ofNullable(internalPort).map(IntOrString::new).ifPresent(newServicePort::setTargetPort);
        return newServicePort;
    }

    public boolean updateValueInConfigMap(final String mapName, final String namespace,
                                          final String key, final String value) {
        try (KubernetesClient client = getKubernetesClient()) {
            client.configMaps()
                .inNamespace(defaultNamespaceIfEmpty(namespace))
                .withName(mapName)
                .edit()
                .addToData(key, value)
                .done();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String defaultNamespaceIfEmpty(final String namespace) {
        return Optional.ofNullable(namespace).filter(StringUtils::isNotEmpty).orElse(kubeNamespace);
    }

    public boolean removeValueFromConfigMap(final String mapName, final String namespace, final String key) {
        try (KubernetesClient client = getKubernetesClient()) {
            client.configMaps()
                .inNamespace(defaultNamespaceIfEmpty(namespace))
                .withName(mapName)
                .edit()
                .removeFromData(key)
                .done();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public Optional<ConfigMap> findConfigMap(final String mapName, final String namespace) {
        try (KubernetesClient client = getKubernetesClient()) {
            return Optional.ofNullable(client.configMaps()
                                           .inNamespace(defaultNamespaceIfEmpty(namespace))
                                           .withName(mapName)
                                           .get());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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

    private void modifyNodeLabel(String nodeName, String labelName, Consumer<Map<String, String>> actionOnLabel) {
        if (StringUtils.isBlank(nodeName) || StringUtils.isBlank(labelName)) {
            return;
        }
        try (KubernetesClient client = getKubernetesClient()) {
            Node node = getNode(client, nodeName);

            Map<String, String> labels = node.getMetadata().getLabels();

            actionOnLabel.accept(labels);

            node.getMetadata().setLabels(labels);
            client.nodes().createOrReplace(node);
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public Map<String, String> getNodeLabels(final KubernetesClient client, final String nodeName) {
        if (StringUtils.isBlank(nodeName)) {
            return Collections.emptyMap();
        }
        try {
            return findNode(client, nodeName)
                    .map(Node::getMetadata)
                    .map(ObjectMeta::getLabels)
                    .orElseGet(Collections::emptyMap);
        } catch (KubernetesClientException e) {
            LOGGER.error(e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private Node getNode(final KubernetesClient client, final String nodeName) {
        return findNode(client, nodeName).orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_NODE_NOT_FOUND, nodeName)));
    }

    private Optional<Node> findNode(final KubernetesClient client, final String nodeName) {
        return Optional.ofNullable(client.nodes().withName(nodeName).get());
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

    public void waitForNodeReady(String nodeName, String runId, String cloudRegion) throws InterruptedException {
        KubernetesClient client = getKubernetesClient();
        final int nodeReadyTimeout = preferenceManager.getPreference(SystemPreferences.CLUSTER_NODE_READY_TIMEOUT);
        final int nodeReadyStatusCheckTimeout = preferenceManager.getPreference(
                SystemPreferences.CLUSTER_NODE_READY_STATUS_CHECK_TIMEOUT);
        final int attemptsStatusNode = nodeReadyTimeout / nodeReadyStatusCheckTimeout;
        int attempts = attemptsStatusNode;
        while (!isReadyNode(nodeName, client)) {
            LOGGER.debug("Waiting for node {} is ready.", nodeName);
            attempts -= 1;
            Thread.sleep(nodeReadyStatusCheckTimeout);
            if (attempts <= 0) {
                throw new IllegalStateException(String.format(
                        "Node %s doesn't match the ready status over than %d times.", nodeName, attemptsStatusNode));
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
        return allPodsAreReady(list);
    }

    private boolean allPodsAreReady(final PodList list) {
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

    public void getOrCreatePodDnsService() {
        final String name = preferenceManager.getPreference(SystemPreferences.KUBE_POD_SERVICE);
        try(KubernetesClient client = getKubernetesClient()) {
            final Optional<Service> service = findServiceByName(client, name);
            if (service.isPresent()) {
                return;
            }
            final Service newService = client.services().createNew()
                    .withNewMetadata()
                    .withName(name)
                    .withNamespace(kubeNamespace)
                    .endMetadata()
                    .withNewSpec()
                    .withClusterIP("None")
                    .withType("ClusterIP")
                    .withSelector(Collections.singletonMap(KubernetesConstants.TYPE_LABEL,
                            KubernetesConstants.PIPELINE_TYPE))
                    .endSpec()
                    .done();
            Assert.notNull(newService, messageHelper.getMessage(MessageConstants.ERROR_KUBE_SERVICE_CREATE, name));
        }
    }

    public boolean isValidAnnotation(final String annotation) {
        return StringUtils.isNotBlank(annotation)
               && annotation.length() < kubeAnnotationLengthLimit;
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
        return !isLastConditionUnavailable(node);
    }

    public boolean isNodeUnavailable(final Node node) {
        return isLastConditionUnavailable(node);
    }

    private boolean isLastConditionUnavailable(final Node node) {
        return getLastCondition(node)
                .map(NodeCondition::getReason)
                .map(lastReason -> {
                    for (String reason : KubernetesConstants.NODE_OUT_OF_ORDER_REASONS) {
                        if (lastReason.contains(reason)) {
                            log.debug("Node is out of order: {}", node.getStatus().getConditions());
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(true);
    }

    public Optional<LocalDateTime> getLastConditionDateTime(final Node node) {
        return getLastCondition(node)
                .map(NodeCondition::getLastHeartbeatTime)
                .map(dateTime -> LocalDateTime.parse(dateTime, KubernetesConstants.KUBE_DATE_FORMATTER));
    }

    private Optional<NodeCondition> getLastCondition(final Node node) {
        return Optional.ofNullable(node)
                .map(Node::getStatus)
                .map(NodeStatus::getConditions)
                .orElseGet(Collections::emptyList)
                .stream()
                .findFirst();
    }

    public void createNodeService(final RunInstance instance) {
        if (KubernetesConstants.WINDOWS.equals(instance.getNodePlatform())) {
            final Integer port = preferenceManager.getPreference(SystemPreferences.CLUSTER_KUBE_WINDOWS_SERVICE_PORT);
            if (port == null) {
                log.debug("Kubernetes Windows service port is not specified. No service will be created.");
                return;
            }
            final String serviceName = resolveNodeServiceName(instance.getNodeIP());
            createServiceIfNotExists(serviceName, port, port, false);
            createEndpointsIfNotExists(serviceName, instance.getNodeIP(), port);
        }
    }

    public void deleteNodeService(final RunInstance instance) {
        if (KubernetesConstants.WINDOWS.equals(instance.getNodePlatform())) {
            final String serviceName = resolveNodeServiceName(instance.getNodeIP());
            deleteServiceIfExists(serviceName);
            deleteEndpointsIfExist(serviceName);
        }
    }

    private String resolveNodeServiceName(final String ip) {
        return "ip-" + ip.replace(".", "-");
    }

    public Service createServiceIfNotExists(final String name, final int port, final int targetPort,
                                            final boolean setPortName) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Optional<Service> service = findServiceByName(client, name);
            if (service.isPresent()) {
                LOGGER.debug("Service with name '{}' already exists", name);
                return service.get();
            }
            return createService(client, name, port, targetPort, setPortName);
        }
    }

    private Service createService(final KubernetesClient client, final String name, final int port, 
                                  final int targetPort, final boolean setPortName) {
        final ServicePort servicePort = new ServicePort();
        servicePort.setPort(port);
        servicePort.setTargetPort(new IntOrString(targetPort));
        if(setPortName) {
            servicePort.setName(getServicePortName(name, port));
        }
        return createService(client, name, Collections.emptyMap(), Collections.singletonList(servicePort));
    }

    public Service createService(final String serviceName, final Map<String, String> labels,
                                 final List<ServicePort> ports) {
        return createService(serviceName, labels, Collections.emptyMap(), ports, labels);
    }

    public Service createService(final String serviceName, final Map<String, String> labels,
                                 final Map<String, String> annotations, final List<ServicePort> ports,
                                 final Map<String, String> selector) {
        try (KubernetesClient client = getKubernetesClient()) {
            return createService(client, serviceName, labels, annotations, ports, selector);
        }
    }

    private Service createService(final KubernetesClient client, final String serviceName,
                                  final Map<String, String> labels, final List<ServicePort> ports) {
        return createService(client, serviceName, labels, Collections.emptyMap(), ports, labels);
    }

    private Service createService(final KubernetesClient client, final String serviceName,
                                  final Map<String, String> labels,
                                  final Map<String, String> annotations,
                                  final List<ServicePort> ports,
                                  final Map<String, String> selector) {
        final Service service = client.services().createNew()
                .withNewMetadata()
                .withName(serviceName)
                .withNamespace(kubeNamespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withSelector(selector)
                .endSpec()
                .done();
        Assert.notNull(service, messageHelper.getMessage(MessageConstants.ERROR_KUBE_SERVICE_CREATE, serviceName));
        return service;
    }

    public Optional<Integer> generateFreeTargetPort() {
        try (KubernetesClient client = getKubernetesClient()) {
            return client.services().list().getItems().stream()
                .map(Service::getSpec)
                .map(ServiceSpec::getPorts)
                .flatMap(Collection::stream)
                .map(ServicePort::getTargetPort)
                .map(IntOrString::getIntVal)
                .max(Comparator.naturalOrder())
                .map(value -> value + 1)
                .map(Optional::of)
                .orElse(Optional.of(defaultKubeServiceTargetPort));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<Service> getService(final String labelName, final String labelValue) {
        try (KubernetesClient client = getKubernetesClient()) {
            return findServiceByLabel(client, labelName, labelValue);
        }
    }

    public Optional<Service> findServiceByName(final String serviceName) {
        try (KubernetesClient client = getKubernetesClient()) {
            return findServiceByName(client, serviceName);
        }
    }

    public boolean deleteService(final Service service) {
        try (KubernetesClient client = getKubernetesClient()) {
            return deleteService(client, service);
        }
    }

    public boolean deleteServiceIfExists(final String name) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Optional<Service> service = findServiceByName(client, name);
            if (!service.isPresent()) {
                LOGGER.debug("Failed to find service with name '{}'", name);
                return false;
            }
            return deleteService(client, service.get());
        }
    }

    private boolean deleteService(final KubernetesClient client, final Service service) {
        final boolean deleted = Optional.ofNullable(client.services().delete(service)).orElse(false);
        if (!deleted) {
            LOGGER.debug("Failed to delete service '{}'", service.getMetadata().getName());
        }
        return deleted;
    }

    private Optional<Service> findServiceByLabel(final KubernetesClient client, final String labelName,
                                                 final String labelValue) {
        final List<Service> items = findServicesByLabel(client, labelName, labelValue);
        if (CollectionUtils.isEmpty(items)) {
            return Optional.empty();
        }
        if (items.size() > 1) {
            LOGGER.error("More than one service was found for label {}={}.", labelName, labelValue);
        }
        return Optional.of(items.get(0));
    }

    private Optional<Service> findServiceByName(final KubernetesClient client, final String name) {
        return Optional.ofNullable(client.services()
                .inNamespace(kubeNamespace)
                .withName(name)
                .get());
    }

    private Endpoints createEndpointsIfNotExists(final String name, final String ip, final Integer port) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Optional<Endpoints> endpoints = findEndpointsByName(client, name);
            if (endpoints.isPresent()) {
                LOGGER.debug("Endpoints with name '{}' already exist", name);
                return endpoints.get();
            }
            return createEndpoints(client, name, ip, port);
        }
    }

    private Endpoints createEndpoints(final KubernetesClient client, final String name, final String ip, 
                                      final int port) {
        final EndpointAddress endpointAddress = new EndpointAddress();
        endpointAddress.setIp(ip);
        final EndpointPort endpointPort = new EndpointPort();
        endpointPort.setPort(port);
        final EndpointSubset endpointSubset = new EndpointSubset(Collections.singletonList(endpointAddress), 
                Collections.emptyList(), Collections.singletonList(endpointPort));
        final Endpoints endpoints = client.endpoints().createNew()
                .withNewMetadata()
                .withName(name)
                .withNamespace(kubeNamespace)
                .endMetadata()
                .withSubsets(endpointSubset)
                .done();
        Assert.notNull(endpoints, messageHelper.getMessage(MessageConstants.ERROR_KUBE_ENDPOINTS_CREATE, name));
        return endpoints;
    }

    public void deleteEndpointsIfExist(final String name) {
        try (KubernetesClient client = getKubernetesClient()) {
            final Optional<Endpoints> endpoints = findEndpointsByName(client, name);
            if (!endpoints.isPresent()) {
                LOGGER.debug("Failed to find endpoints with name '{}'", name);
                return;
            }
            deleteEndpoints(client, endpoints.get());
        }
    }

    private boolean deleteEndpoints(final KubernetesClient client, final Endpoints endpoints) {
        final boolean deleted = Optional.ofNullable(client.endpoints().delete(endpoints)).orElse(false);
        if (!deleted) {
            LOGGER.debug("Failed to delete endpoints '{}'", endpoints.getMetadata().getName());
        }
        return deleted;
    }

    private Optional<Endpoints> findEndpointsByName(final KubernetesClient client, final String name) {
        return Optional.ofNullable(client.endpoints()
                .inNamespace(kubeNamespace)
                .withName(name)
                .get());
    }

    private List<Service> findServicesByLabel(final KubernetesClient client, final String labelName,
                                              final String labelValue) {
        return client.services()
                .withLabel(labelName, labelValue)
                .list()
                .getItems();
    }

    private List<Service> findServicesByLabels(final KubernetesClient client, final Map<String, String> labels) {
        return client.services()
                .withLabels(labels)
                .list()
                .getItems();
    }
}
