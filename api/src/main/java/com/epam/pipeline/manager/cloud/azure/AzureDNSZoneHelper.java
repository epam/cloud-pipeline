/*
 * Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.azure;

import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.region.AzureRegion;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureHelper;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.dns.DnsZone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureDNSZoneHelper {

    private static final long TTL_TIME = 60L;
    private static final String EMPTY = "";

    public InstanceDNSRecord createDNSRecord(final AzureRegion azureRegion,
                                             final String hostedZoneId, final InstanceDNSRecord dnsRecord) {
        return createDNSRecord(azureRegion, hostedZoneId, dnsRecord, 5);
    }

    public InstanceDNSRecord removeDNSRecord(final AzureRegion azureRegion,
                                             final String hostedZoneId, final InstanceDNSRecord dnsRecord) {
        return removeDNSRecord(azureRegion, hostedZoneId, dnsRecord, 5);
    }

    public InstanceDNSRecord removeDNSRecord(final AzureRegion azureRegion, final String hostedZoneId,
                                             final InstanceDNSRecord dnsRecord, final int retry) {
        log.info("Removing DNS record: {} for target: {} in hostedZoneId: {}",
                dnsRecord.getDnsRecord(), dnsRecord.getTarget(), hostedZoneId);
        try {
            final Azure azure = AzureHelper.buildClient(azureRegion.getAuthFile());
            final DnsZone rootDnsZone = azure.dnsZones().getById(hostedZoneId);

            if (isDnsRecordDoesntExist(dnsRecord, rootDnsZone)) {
                log.warn("DNS record: {} type: {} for target: {} in hostedZoneId: {} doesn't exists",
                        dnsRecord.getDnsRecord(), aName(dnsRecord.getTarget()) ? "A" : "CNAME",
                        dnsRecord.getTarget(), hostedZoneId);
            } else {
                if (aName(dnsRecord.getTarget())) {
                    rootDnsZone.update()
                            .withoutARecordSet(extractRecordSetName(dnsRecord, rootDnsZone))
                            .apply();
                } else {
                    rootDnsZone.update()
                            .withoutCNameRecordSet(extractRecordSetName(dnsRecord, rootDnsZone))
                            .apply();
                }
            }
        } catch (Exception e) {
            if (retry < 1) {
                throw new IllegalStateException("Exceeded retry count, error: ", e);
            }
            log.warn("Retry to remove DNS record in hostedZoneId: {} record: {} and target: {} error message: {}",
                    hostedZoneId, dnsRecord.getDnsRecord(), dnsRecord.getTarget(), e.getMessage());
            return removeDNSRecord(azureRegion, hostedZoneId, dnsRecord, retry - 1);
        }
        return new InstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(),
                InstanceDNSRecord.DNSRecordStatus.INSYNC);

    }

    private InstanceDNSRecord createDNSRecord(final AzureRegion azureRegion, final String hostedZoneId,
                                              final InstanceDNSRecord dnsRecord, final int retry) {
        log.info("Creating DNS record for hostedZoneId: {} record: {} and target: {}",
                hostedZoneId, dnsRecord.getDnsRecord(), dnsRecord.getTarget());
        try {
            final Azure azure = AzureHelper.buildClient(azureRegion.getAuthFile());
            final DnsZone rootDnsZone = azure.dnsZones().getById(hostedZoneId);
            if (isDnsRecordDoesntExist(dnsRecord, rootDnsZone)) {
                if (aName(dnsRecord.getTarget())) {
                    rootDnsZone.update()
                            .defineARecordSet(extractRecordSetName(dnsRecord, rootDnsZone))
                            .withIPv4Address(dnsRecord.getTarget())
                            .withTimeToLive(TTL_TIME)
                            .attach()
                            .apply();
                } else {
                    rootDnsZone.update()
                            .defineCNameRecordSet(extractRecordSetName(dnsRecord, rootDnsZone))
                            .withAlias(dnsRecord.getTarget())
                            .withTimeToLive(TTL_TIME)
                            .attach()
                            .apply();
                }
            }
        } catch (Exception e) {
            if (retry < 1) {
                throw new IllegalStateException("Exceeded retry count, error: ", e);
            }
            log.warn("Retry to create DNS reccord in hostedZoneId: {} record: {} and target: {} error message: {}",
                    hostedZoneId, dnsRecord.getDnsRecord(), dnsRecord.getTarget(), e.getMessage());
            return createDNSRecord(azureRegion, hostedZoneId, dnsRecord, retry - 1);
        }


        return new InstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(),
                InstanceDNSRecord.DNSRecordStatus.INSYNC);
    }

    private String extractRecordSetName(final InstanceDNSRecord dnsRecord, final DnsZone rootDnsZone) {
        return dnsRecord.getDnsRecord().replace("." + rootDnsZone.name(), EMPTY);
    }

    private boolean isDnsRecordDoesntExist(final InstanceDNSRecord dnsRecord, final DnsZone rootDnsZone) {
        return rootDnsZone.listRecordSets(extractRecordSetName(dnsRecord, rootDnsZone)).isEmpty();
    }

    private static boolean aName(final String target) {
        return target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
}
