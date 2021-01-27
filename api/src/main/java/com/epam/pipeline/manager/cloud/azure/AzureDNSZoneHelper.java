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
        log.info("Creating DNS record for hostedZoneId: {} record: {} and target: {}",
                hostedZoneId, dnsRecord.getDnsRecord(), dnsRecord.getTarget());
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

        return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(),
                InstanceDNSRecord.DNSRecordStatus.INSYNC);
    }

    private String extractRecordSetName(final InstanceDNSRecord dnsRecord, final DnsZone rootDnsZone) {
        return dnsRecord.getDnsRecord().replace("." + rootDnsZone.name(), EMPTY);
    }

    public InstanceDNSRecord removeDNSRecord(final AzureRegion azureRegion,
                                             final String hostedZoneId, final InstanceDNSRecord dnsRecord) {
        log.info("Removing DNS record: {} for target: {} in hostedZoneId: {}",
                dnsRecord.getDnsRecord(), dnsRecord.getTarget(), hostedZoneId);
        final Azure azure = AzureHelper.buildClient(azureRegion.getAuthFile());
        final DnsZone rootDnsZone = azure.dnsZones().getById(hostedZoneId);

        if (isDnsRecordDoesntExist(dnsRecord, rootDnsZone)) {
            log.info("DNS record: {} type: {} for target: {} in hostedZoneId: {} doesn't exists",
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
        return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(),
                InstanceDNSRecord.DNSRecordStatus.INSYNC);

    }

    private boolean isDnsRecordDoesntExist(final InstanceDNSRecord dnsRecord, final DnsZone rootDnsZone) {
        return rootDnsZone.listRecordSets(dnsRecord.getDnsRecord()).isEmpty();
    }

    private InstanceDNSRecord buildInstanceDNSRecord(final String dnsRecord,
                                                     final String target,
                                                     final InstanceDNSRecord.DNSRecordStatus status) {
        return new InstanceDNSRecord(dnsRecord, target, status);
    }

    private InstanceDNSRecord.DNSRecordStatus getStatus(final String status) {
        return InstanceDNSRecord.DNSRecordStatus.valueOf(status);
    }

    private static boolean aName(final String target) {
        return target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
    }
}
