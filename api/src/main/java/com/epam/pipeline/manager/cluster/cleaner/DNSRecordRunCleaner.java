/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.manager.cloud.aws.Route53Helper;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.utils.UtilsManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class DNSRecordRunCleaner implements RunCleaner {

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static final String DELIMITER = "/";
    private static final String PORT_DELIMITER = ":";

    private final PreferenceManager preferenceManager;
    private final UtilsManager utilsManager;
    private final Route53Helper route53Helper;
    private final ObjectMapper mapper = new ObjectMapper();

    public DNSRecordRunCleaner(final PreferenceManager preferenceManager,
                               final UtilsManager utilsManager,
                               final Route53Helper route53Helper) {
        this.preferenceManager = preferenceManager;
        this.utilsManager = utilsManager;
        this.route53Helper = route53Helper;
    }

    @Override
    public void cleanResources(final PipelineRun run) {
        final String serviceUrls = run.getServiceUrl();
        if (StringUtils.isEmpty(serviceUrls)) {
            return;
        }

        final String hostZoneId = preferenceManager.getPreference(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_ID);
        final String hostZoneUrlBase = preferenceManager.getPreference(SystemPreferences.INSTANCE_DNS_HOSTED_ZONE_BASE);
        Assert.isTrue(
                !StringUtils.isEmpty(hostZoneId) && !StringUtils.isEmpty(hostZoneUrlBase),
                "instance.dns.hosted.zone.id or instance.dns.hosted.zone.base is empty can't remove DNS record."
        );

        try {
            final JsonNode serviceUrlsNode = mapper.readTree(serviceUrls);
            serviceUrlsNode.iterator().forEachRemaining(jsonNode -> {
                final Map<String, String> map = mapper.convertValue(
                        jsonNode,
                        new TypeReference<Map<String, String>>(){}
                );

                final String url = map.get("url");
                if (!StringUtils.isEmpty(url) && url.contains(hostZoneUrlBase)) {
                    route53Helper.removeDNSRecord(hostZoneId, new InstanceDNSRecord(unify(url), utilsManager.getEdgeDomainNameOrIP(), null));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String unify(final String url) {
        return url.trim()
                .replace(HTTP, "")
                .replace(HTTPS, "")
                .split(DELIMITER)[0]
                .split(PORT_DELIMITER)[0];
    }

}
