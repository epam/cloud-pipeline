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

package com.epam.pipeline.manager.cluster.cleaner;

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordFormat;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordStatus;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.EdgeServiceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunServiceUrlManager;
import com.epam.pipeline.utils.URLUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class DNSRecordRunCleaner implements RunCleaner {

    private final CloudFacade cloudFacade;
    private final EdgeServiceManager edgeServiceManager;
    private final PipelineRunServiceUrlManager pipelineRunServiceUrlManager;

    @Override
    public void cleanResources(final PipelineRun run) {
        cleanResources(run.getId());
    }

    @Override
    public void cleanResources(final Long runId) {
        pipelineRunServiceUrlManager.loadByRunId(runId).forEach(this::removeDNSRecords);
    }

    private void removeDNSRecords(final String regionName, final String serializedServiceUrls) {
        final String edgeUrl = edgeServiceManager.getEdgeDomainNameOrIP(regionName);
        for (final ServiceUrl serviceUrl : getDeserializedServiceUrls(serializedServiceUrls)) {
            if (!serviceUrl.isCustomDNS()) {
                continue;
            }
            final String domain = URLUtils.getHost(serviceUrl.getUrl());
            final InstanceDNSRecord record = new InstanceDNSRecord(domain, edgeUrl,
                    InstanceDNSRecordFormat.ABSOLUTE, InstanceDNSRecordStatus.NOOP);
            cloudFacade.removeDNSRecord(serviceUrl.getRegionId(), record);
        }
    }

    private List<ServiceUrl> getDeserializedServiceUrls(final String serializedServiceUrls) {
        return deserializedServiceUrls(serializedServiceUrls)
                .collect(Collectors.toList());
    }

    private Stream<ServiceUrl> deserializedServiceUrls(final String serializedServiceUrls) {
        return Optional.ofNullable(deserializeServiceUrls(serializedServiceUrls))
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .filter(serviceUrl -> StringUtils.isNotBlank(serviceUrl.getUrl()) && serviceUrl.getRegionId() != null);
    }

    private List<ServiceUrl> deserializeServiceUrls(final String serializedServiceUrls) {
        final ObjectMapper mapper = JsonMapper.newInstance()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return JsonMapper.parseData(serializedServiceUrls, new TypeReference<List<ServiceUrl>>() {}, mapper);
    }

    @Value
    private static class ServiceUrl {
        String url;
        Long regionId;
        boolean customDNS;
    }
}
