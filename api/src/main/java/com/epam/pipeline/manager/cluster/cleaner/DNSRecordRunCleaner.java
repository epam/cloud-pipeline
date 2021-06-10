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
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cloud.CloudFacade;
import com.epam.pipeline.manager.cluster.EdgeServiceManager;
import com.epam.pipeline.manager.pipeline.PipelineRunServiceUrlManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DNSRecordRunCleaner implements RunCleaner {

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String DELIMITER = "/";
    private static final String PORT_DELIMITER = ":";

    private final PreferenceManager preferenceManager;
    private final EdgeServiceManager edgeServiceManager;
    private final CloudFacade cloudFacade;
    private final PipelineRunServiceUrlManager pipelineRunServiceUrlManager;

    public DNSRecordRunCleaner(final PreferenceManager preferenceManager,
                               final EdgeServiceManager edgeServiceManager,
                               final CloudFacade cloudFacade,
                               final PipelineRunServiceUrlManager pipelineRunServiceUrlManager) {
        this.preferenceManager = preferenceManager;
        this.edgeServiceManager = edgeServiceManager;
        this.cloudFacade = cloudFacade;
        this.pipelineRunServiceUrlManager = pipelineRunServiceUrlManager;
    }

    @Override
    public void cleanResources(final PipelineRun run) {
        final Map<String, String> serviceUrls = pipelineRunServiceUrlManager.loadByRunId(run.getId());
        if (MapUtils.isEmpty(serviceUrls)) {
            return;
        }

        final String hostZoneId = preferenceManager.getPreference(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_ID);
        final String hostZoneUrlBase = preferenceManager.getPreference(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_BASE);

        if (StringUtils.isEmpty(hostZoneId) || StringUtils.isEmpty(hostZoneUrlBase)) {
            return;
        }

        final Map<String, String> edgeDomainNameOrIP = edgeServiceManager.getEdgeDomainNameOrIP();

        serviceUrls.forEach((region, serviceUrlsRawString) -> {
            final String nameOrIP = edgeDomainNameOrIP.get(region);
            ListUtils.emptyIfNull(parseServiceUrl(serviceUrlsRawString))
                    .forEach(serviceUrl ->
                            removeDnsRecord(nameOrIP, serviceUrl, hostZoneUrlBase, hostZoneId, run, region));
        });
    }

    @Override
    public void cleanResources(final Long runId) {
        log.error("Clearing resource via runId is not supported.");
    }

    private static String unify(final String url) {
        return url.trim()
                .replace(HTTP, "")
                .replace(HTTPS, "")
                .split(DELIMITER)[0]
                .split(PORT_DELIMITER)[0];
    }

    private List<Map<String, String>> parseServiceUrl(final String serviceUrlString) {
        return JsonMapper.parseData(serviceUrlString, new TypeReference<List<Map<String, String>>>(){});
    }

    private void removeDnsRecord(final String nameOrIP, final Map<String, String> serviceUrlObject,
                                 final String hostZoneUrlBase, final String hostZoneId, final PipelineRun run,
                                 final String region) {
        if (StringUtils.isEmpty(nameOrIP)) {
            log.warn("Cannot find edge service for requested region '{}'", region);
            return;
        }
        final String url = serviceUrlObject.get("url");
        if (!StringUtils.isEmpty(url) && url.contains(hostZoneUrlBase)) {
            validateHostZone(hostZoneUrlBase, hostZoneId);
            cloudFacade.removeDNSRecord(run.getInstance().getCloudRegionId(),
                    new InstanceDNSRecord(unify(url), nameOrIP, null));
        }
    }

    private void validateHostZone(final String hostZoneUrlBase, final String hostZoneId) {
        Assert.isTrue(!StringUtils.isEmpty(hostZoneId) && !StringUtils.isEmpty(hostZoneUrlBase),
                "instance.dns.hosted.zone.id or instance.dns.hosted.zone.base is empty can't remove DNS record."
        );
    }
}
