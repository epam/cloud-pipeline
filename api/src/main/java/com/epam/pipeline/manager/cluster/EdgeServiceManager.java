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
import com.epam.pipeline.entity.cluster.PrettyUrl;
import com.epam.pipeline.entity.cluster.ServiceDescription;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.pipeline.PipelineRunCRUDService;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EdgeServiceManager {
    private static final String DEFAULT_SVC_SCHEME = "http";
    private static final String BASE_URL_TEMPLATE = "%s://%s:%d";
    private static final String SSH_URL_TEMPLATE = "%s://%s:%d/ssh/pipeline/%s";
    private static final String FSBROWSER_URL_TEMPLATE = "%s://%s:%d/fsbrowser/%s";

    private final KubernetesManager kubernetesManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;
    private final PipelineRunCRUDService runCRUDService;
    private final String kubeEdgeIpLabel;
    private final String kubeEdgePortLabel;
    private final String kubeEdgeSchemeLabel;
    private final String kubeEdgeRegionLabel;
    private final String edgeLabel;

    public EdgeServiceManager(final KubernetesManager kubernetesManager,
                              final MessageHelper messageHelper,
                              final PreferenceManager preferenceManager,
                              final PipelineRunCRUDService runCRUDService,
                              @Value("${kube.edge.ip.label}") final String kubeEdgeIpLabel,
                              @Value("${kube.edge.port.label}") final String kubeEdgePortLabel,
                              @Value("${kube.edge.scheme.label:cloud-pipeline/external-scheme}")
                              final String kubeEdgeSchemeLabel,
                              @Value("${kube.edge.region.label:cloud-pipeline/region}")
                              final String kubeEdgeRegionLabel,
                              @Value("${kube.edge.label}") final String edgeLabel) {
        this.kubernetesManager = kubernetesManager;
        this.messageHelper = messageHelper;
        this.preferenceManager = preferenceManager;
        this.runCRUDService = runCRUDService;
        this.kubeEdgeIpLabel = kubeEdgeIpLabel;
        this.kubeEdgePortLabel = kubeEdgePortLabel;
        this.kubeEdgeSchemeLabel = kubeEdgeSchemeLabel;
        this.kubeEdgeRegionLabel = kubeEdgeRegionLabel;
        this.edgeLabel = edgeLabel;
    }

    public Map<String, String> buildSshUrl(final Long runId) {
        return buildServiceUrl(SSH_URL_TEMPLATE, toPrettyUrlPath(runId).orElse(ObjectUtils.toString(runId)));
    }

    private Optional<String> toPrettyUrlPath(final Long runId) {
        return runCRUDService.findRunByRunId(runId)
                .map(PipelineRun::getPrettyUrl)
                .flatMap(this::toPrettyUrl)
                .map(PrettyUrl::getPath);
    }

    private Optional<PrettyUrl> toPrettyUrl(final String prettyUrlJson) {
        try {
            return Optional.ofNullable(JsonMapper.parseData(prettyUrlJson, new TypeReference<PrettyUrl>() {}));
        } catch (IllegalArgumentException e) {
            log.warn("Pretty url parsing has failed: {}", prettyUrlJson, e);
            return Optional.empty();
        }
    }

    public String getEdgeDomainNameOrIP(final String region) {
        final Map<String, String> labels = buildRegionsLabels(region);
        return kubernetesManager.getServicesByLabels(labels, edgeLabel).stream()
                .findFirst()
                .map(this::getServiceDescription)
                .map(ServiceDescription::getIp)
                .orElseGet(this::logServiceNotFoundAndReturnNull);
    }

    public Map<String, String> buildFSBrowserUrl(final Long runId) {
        if (isFSBrowserEnabled() && runIsNotSensitive(runId)) {
            return buildServiceUrl(FSBROWSER_URL_TEMPLATE, ObjectUtils.toString(runId));
        } else {
            throw new IllegalArgumentException("Storage fsbrowser is not enabled.");
        }
    }

    public String buildEdgeExternalUrl(final String region) {
        return getEdgeForRegionOrDefault(region)
                .stream()
                .findFirst()
                .map(edgeService -> buildEdgeExternalUrl(getServiceDescription(edgeService)))
                .orElseGet(this::logServiceNotFoundAndReturnNull);
    }

    public List<ServiceDescription> getEdgeServices() {
        final List<Service> edgeServices = kubernetesManager.getServicesByLabel(edgeLabel);
        if (CollectionUtils.isEmpty(edgeServices)) {
            throw new IllegalArgumentException("Edge server is not registered in the cluster.");
        }
        return edgeServices.stream()
                .map(this::getServiceDescription)
                .collect(Collectors.toList());
    }

    private ServiceDescription getServiceDescription(final Service service) {
        final Map<String, String> labels = service.getMetadata().getLabels();
        final String scheme = getValueFromLabelsOrDefault(labels, kubeEdgeSchemeLabel, () -> DEFAULT_SVC_SCHEME);
        final String ip = getValueFromLabelsOrDefault(labels, kubeEdgeIpLabel, () -> getExternalIp(service));
        final Integer port = getServicePort(service);
        final String region = getValueFromLabelsOrDefault(labels, kubeEdgeRegionLabel, () -> StringUtils.EMPTY);
        return new ServiceDescription(scheme, ip, port, region);
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
                kubeEdgePortLabel, () -> getDefaultServicePort(service));
        if (NumberUtils.isDigits(portLabelValue)) {
            return Integer.parseInt(portLabelValue);
        }
        throw new IllegalArgumentException(messageHelper.getMessage(
                MessageConstants.ERROR_KUBE_SERVICE_PORT_UNDEFINED, service.getMetadata().getName()));
    }

    private String getDefaultServicePort(final Service service) {
        final Integer port = ListUtils.emptyIfNull(service.getSpec().getPorts()).stream()
                .findFirst()
                .map(ServicePort::getPort)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_KUBE_SERVICE_PORT_UNDEFINED, service.getMetadata().getName())));
        return String.valueOf(port);
    }

    private String getValueFromLabelsOrDefault(final Map<String, String> labels,
                                               final String labelName,
                                               final Supplier<String> defaultSupplier) {
        if (StringUtils.isBlank(labelName) || StringUtils.isBlank(MapUtils.emptyIfNull(labels).get(labelName))) {
            return defaultSupplier.get();
        }
        return labels.get(labelName);
    }

    private boolean runIsNotSensitive(final Long runId) {
        return !BooleanUtils.toBoolean(runCRUDService.loadRunById(runId).getSensitive());
    }

    private Boolean isFSBrowserEnabled() {
        return Optional.ofNullable(preferenceManager.getBooleanPreference(
                SystemPreferences.STORAGE_FSBROWSER_ENABLED.getKey()))
                .orElse(false);
    }

    private String buildEdgeExternalUrl(final ServiceDescription edgeService) {
        return String.format(BASE_URL_TEMPLATE, edgeService.getScheme(), edgeService.getIp(), edgeService.getPort());
    }

    private Map<String, String> buildRegionsLabels(final String region) {
        final Map<String, String> labels = new HashMap<>();
        if (StringUtils.isNotBlank(region)) {
            labels.put(kubeEdgeRegionLabel, region);
        }
        return labels;
    }

    private Map<String, String> buildServiceUrl(final String template, final String path) {
        return kubernetesManager.getServicesByLabel(edgeLabel).stream()
                .map(this::getServiceDescription)
                .collect(Collectors.toMap(ServiceDescription::getRegion, serviceDescription ->
                        buildUrl(template, path, serviceDescription)));
    }

    private String buildUrl(final String template, final String path, final ServiceDescription service) {
        return String.format(template, service.getScheme(), service.getIp(), service.getPort(), path);
    }

    private String getEdgeRegion(final String region) {
        return StringUtils.isBlank(region)
                ? preferenceManager.getPreference(SystemPreferences.DEFAULT_EDGE_REGION)
                : region;
    }

    private String logServiceNotFoundAndReturnNull() {
        log.debug("Could not find any edge service");
        return null;
    }

    private List<Service> getEdgeForRegionOrDefault(final String region) {
        final List<Service> services = getEdgeServiceForRegion(region);
        if (CollectionUtils.isNotEmpty(services)) {
            return services;
        }
        return getEdgeServiceForRegion(null);
    }

    private List<Service> getEdgeServiceForRegion(final String region) {
        final Map<String, String> labels = buildRegionsLabels(getEdgeRegion(region));
        return kubernetesManager.getServicesByLabels(labels, edgeLabel);
    }
}
