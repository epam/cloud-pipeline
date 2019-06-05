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

package com.epam.pipeline.vmmonitor.service.impl;

import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.vmmonitor.exception.GCPMonitorException;
import com.epam.pipeline.vmmonitor.model.vm.VMTag;
import com.epam.pipeline.vmmonitor.model.vm.VirtualMachine;
import com.epam.pipeline.vmmonitor.service.VMMonitorService;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.NetworkInterface;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GCPMonitorService implements VMMonitorService<GCPRegion> {
    private static final String FILTER_PATTERN = "(status = RUNNING) AND (labels.%s = %s)";

    private final JsonFactory jsonFactory;
    private final HttpTransport httpTransport;
    private final VMTag instanceTag;

    public GCPMonitorService(@Value("${monitor.instance.tag}") final String instanceTagString) {
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            log.error("Can't receive GoogleNetHttpTransport instance!");
            throw new GCPMonitorException("Can't create monitor instance!");
        }
        this.instanceTag = VMTag.fromProperty(instanceTagString);
        this.jsonFactory = JacksonFactory.getDefaultInstance();
    }

    @Override
    public List<VirtualMachine> fetchRunningVms(final GCPRegion region) {
        verifyProjectInfo(region);
        final Compute compute = getGCPCompute(region);
        final InstanceList instances = getGcpVmInstances(region, compute);
        return createListOfVMsFromGCPInstances(instances);
    }

    @Override
    public CloudProvider provider() {
        return CloudProvider.GCP;
    }


    private void verifyProjectInfo(final GCPRegion region) {
        Assert.notNull(region.getProject(), "Project ID is not specified for GCP region.");
        Assert.notNull(region.getRegionCode(), "Zone ID is not specified for GCP region.");
    }

    Compute getGCPCompute(final GCPRegion region) {
        try {
            final GoogleCredential credential = getCredentials(region.getAuthFile());
            return new Compute.Builder(httpTransport, jsonFactory, credential).build();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPMonitorException(e.getMessage(), e);
        }
    }

    private GoogleCredential getCredentials(final String authFile) throws IOException {
        if (StringUtils.isBlank(authFile)) {
            return GoogleCredential.getApplicationDefault();
        }
        try (InputStream stream = new FileInputStream(authFile)) {
            return GoogleCredential.fromStream(stream)
                                   .createScoped(Collections.singletonList(ComputeScopes.COMPUTE_READONLY));
        }
    }

    InstanceList getGcpVmInstances(final GCPRegion region, final Compute compute) {
        final String filter = String.format(FILTER_PATTERN, instanceTag.getKey(), instanceTag.getValue());
        try {
            return Optional.ofNullable(compute.instances()
                                              .list(region.getProject(), region.getRegionCode())
                                              .setFilter(filter)
                                              .execute()).orElse(new InstanceList());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new GCPMonitorException(e.getMessage(), e);
        }
    }

    private List<VirtualMachine> createListOfVMsFromGCPInstances(final InstanceList instances) {
        return ListUtils.emptyIfNull(instances.getItems())
                        .stream()
                        .map(this::toVM)
                        .collect(Collectors.toList());
    }

    private VirtualMachine toVM(final Instance instance) {
        return VirtualMachine.builder()
                             .instanceName(instance.getName())
                             .cloudProvider(provider())
                             .instanceId(instance.getId().toString())
                             .privateIp(getInternalIP(instance))
                             .tags(instance.getLabels())
                             .build();
    }

    private String getInternalIP(Instance instance) {
        final List<NetworkInterface> networkInterfaces = instance.getNetworkInterfaces();
        return CollectionUtils.isNotEmpty(networkInterfaces)
                ? networkInterfaces.get(0).getNetworkIP()
                : null;
    }
}
