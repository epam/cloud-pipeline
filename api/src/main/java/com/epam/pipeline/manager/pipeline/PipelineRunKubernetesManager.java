/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.pipeline.KubernetesService;
import com.epam.pipeline.entity.pipeline.KubernetesServicePort;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cluster.KubernetesManager;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@Slf4j
public class PipelineRunKubernetesManager {
    private static final String SERVICE_POSTFIX = "svc.cluster.local";

    private final PipelineRunCRUDService pipelineRunCRUDService;
    private final KubernetesManager kubernetesManager;
    private final MessageHelper messageHelper;
    private final String runIdLabelName;
    private final String namespace;

    public PipelineRunKubernetesManager(final PipelineRunCRUDService pipelineRunCRUDService,
                                        final KubernetesManager kubernetesManager,
                                        final MessageHelper messageHelper,
                                        @Value("${kube.service.run.id.label:}") final String runIdLabelName,
                                        @Value("${kube.namespace:}") final String namespace) {
        this.pipelineRunCRUDService = pipelineRunCRUDService;
        this.kubernetesManager = kubernetesManager;
        this.messageHelper = messageHelper;
        this.runIdLabelName = runIdLabelName;
        this.namespace = namespace;
    }

    public KubernetesService createKubernetesService(final String serviceName, final Long runId,
                                                     final List<KubernetesServicePort> ports) {
        final PipelineRun pipelineRun = pipelineRunCRUDService.loadRunById(runId);
        final String podId = pipelineRun.getPodId();
        Assert.notNull(kubernetesManager.findPodById(podId), messageHelper.getMessage(
                MessageConstants.ERROR_KUBE_POD_NOT_FOUND, podId));
        final Service service = kubernetesManager
                .createService(serviceName, buildLabels(runId), buildServicePorts(ports));
        final KubernetesService kubernetesService = parseService(service);
        pipelineRunCRUDService.updateKubernetesService(pipelineRun, true);
        return kubernetesService;
    }

    public KubernetesService getKubernetesService(final Long runId) {
        pipelineRunCRUDService.loadRunById(runId);
        final Service service = kubernetesManager.getService(runIdLabelName, String.valueOf(runId));
        if (Objects.isNull(service)) {
            log.debug("No kubernetes service available for run '{}'", runId);
            return null;
        }
        return parseService(service);
    }

    public KubernetesService deleteKubernetesService(final Long runId) {
        final Service service = kubernetesManager.getService(runIdLabelName, String.valueOf(runId));
        if (Objects.isNull(service)) {
            log.debug("No kubernetes service available for run '{}'", runId);
            return null;
        }
        final boolean deleted = kubernetesManager.deleteService(service);
        if (!deleted) {
            return null;
        }
        return parseService(service);
    }

    private Map<String, String> buildLabels(final Long runId) {
        return Collections.singletonMap(runIdLabelName, String.valueOf(runId));
    }

    private List<KubernetesServicePort> parseServicePorts(final ServiceSpec serviceSpec) {
        return ListUtils.emptyIfNull(serviceSpec.getPorts()).stream()
                .map(this::parseServicePort)
                .collect(Collectors.toList());
    }

    private KubernetesServicePort parseServicePort(final ServicePort servicePort) {
        return KubernetesServicePort.builder()
                .port(servicePort.getPort())
                .targetPort(Objects.isNull(servicePort.getTargetPort())
                        ? null
                        : servicePort.getTargetPort().getIntVal())
                .build();
    }

    private KubernetesService parseService(final Service service) {
        final ServiceSpec serviceSpec = service.getSpec();
        final String serviceName = service.getMetadata().getName();
        return KubernetesService.builder()
                .name(serviceName)
                .ports(parseServicePorts(serviceSpec))
                .hostName(buildServiceHostName(serviceName))
                .build();
    }

    private List<ServicePort> buildServicePorts(final List<KubernetesServicePort> ports) {
        return ListUtils.emptyIfNull(ports).stream()
                .map(this::buildServicePort)
                .collect(Collectors.toList());
    }

    private ServicePort buildServicePort(final KubernetesServicePort port) {
        final ServicePort servicePort = new ServicePort();
        servicePort.setPort(port.getPort());
        servicePort.setTargetPort(new IntOrString(port.getTargetPort()));
        return servicePort;
    }

    private String buildServiceHostName(final String serviceName) {
        return String.format("%s.%s.%s", serviceName, namespace, SERVICE_POSTFIX);
    }
}
