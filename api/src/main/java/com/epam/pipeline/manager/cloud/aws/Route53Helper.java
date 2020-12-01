/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.aws;

import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53AsyncClientBuilder;
import com.amazonaws.services.route53.model.*;
import com.amazonaws.services.route53.waiters.AmazonRoute53Waiters;
import com.amazonaws.waiters.WaiterParameters;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class Route53Helper {

    private static final Logger LOGGER = LoggerFactory.getLogger(Route53Helper.class);

    public AmazonRoute53 getRoute53Client() {
        AmazonRoute53AsyncClientBuilder builder = AmazonRoute53AsyncClientBuilder.standard();
        return builder.build();
    }

    public InstanceDNSRecord createDNSRecord(final String hostedZoneId, final InstanceDNSRecord dnsRecord) {
        LOGGER.info("Creating DSN record for hostedZoneId: " + hostedZoneId + " and target: " + dnsRecord.getTarget());
        final AmazonRoute53 client = getRoute53Client();
        if (isDnsRecordExists(hostedZoneId, dnsRecord.getDnsRecord(), client)) {
            return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(), InstanceDNSRecord.DNSRecordStatus.INSYNC.name());
        } else {
            final ChangeResourceRecordSetsResult result = performChangeRequest(hostedZoneId,
                    dnsRecord.getDnsRecord(), dnsRecord.getTarget(), client, ChangeAction.CREATE);
            return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(), result.getChangeInfo().getStatus());
        }
    }

    public InstanceDNSRecord removeDNSRecord(final String hostedZoneId, final InstanceDNSRecord dnsRecord) {
        LOGGER.info("Removing DSN record: " + dnsRecord.getDnsRecord() + " in hostedZoneId: " + hostedZoneId);
        final AmazonRoute53 client = getRoute53Client();
        if (!isDnsRecordExists(hostedZoneId, dnsRecord.getDnsRecord(), client)) {
            return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(), InstanceDNSRecord.DNSRecordStatus.INSYNC.name());
        } else {
            final ChangeResourceRecordSetsResult result = performChangeRequest(hostedZoneId,
                    dnsRecord.getDnsRecord(), dnsRecord.getTarget(), client, ChangeAction.DELETE);
            return buildInstanceDNSRecord(dnsRecord.getDnsRecord(), dnsRecord.getTarget(), result.getChangeInfo().getStatus());
        }

    }

    private boolean isDnsRecordExists(final String hostedZoneId, final String dnsRecord, final AmazonRoute53 client) {
        return client.listResourceRecordSets(new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZoneId)
                .withStartRecordName(dnsRecord)
                .withStartRecordType(RRType.CNAME)).getResourceRecordSets().stream()
                .map(ResourceRecordSet::getName)
                .anyMatch(resourceRecord -> resourceRecord.equalsIgnoreCase(dnsRecord)
                        || resourceRecord.equalsIgnoreCase(dnsRecord + "."));
    }

    private InstanceDNSRecord buildInstanceDNSRecord(final String dnsRecord,
                                                     final String target, final String status) {
        return new InstanceDNSRecord(dnsRecord, target, getStatus(status));
    }

    private InstanceDNSRecord.DNSRecordStatus getStatus(final String status) {
        switch (status) {
            case "PENDING":
                return InstanceDNSRecord.DNSRecordStatus.PENDING;
            case "INSYNC":
                return InstanceDNSRecord.DNSRecordStatus.INSYNC;
            default:
                return InstanceDNSRecord.DNSRecordStatus.NO_OP;
        }
    }

    private ChangeResourceRecordSetsResult performChangeRequest(final String hostedZoneId, final String dnsRecord,
                                                                final String target, final AmazonRoute53 client,
                                                                final ChangeAction action) {
        ChangeResourceRecordSetsResult result = client.changeResourceRecordSets(new ChangeResourceRecordSetsRequest()
                .withHostedZoneId(hostedZoneId)
                .withChangeBatch(new ChangeBatch()
                        .withChanges(
                                new Change()
                                        .withAction(action)
                                        .withResourceRecordSet(
                                                new ResourceRecordSet()
                                                        .withName(dnsRecord)
                                                        .withType(RRType.CNAME)
                                                        .withTTL(60L)
                                                        .withResourceRecords(
                                                                new ResourceRecord().withValue(target)
                                                        )
                                        )
                        )
                )
        );
        WaiterParameters<GetChangeRequest> request = new WaiterParameters<GetChangeRequest>()
                .withRequest(new GetChangeRequest().withId(result.getChangeInfo().getId()));
        new AmazonRoute53Waiters(client).resourceRecordSetsChanged().run(request);
        String status = checkRequestStatus(client, request.getRequest().getId()).getChangeInfo().getStatus();
        if (status.equalsIgnoreCase(ChangeStatus.INSYNC.name())) {
            result.getChangeInfo().setStatus(status);
            return result;
        } else {
            throw new IllegalStateException("Can't create Route53 DNS record for some reason.");
        }
    }

    private GetChangeResult checkRequestStatus(final AmazonRoute53 client, final String requestId) {
        return client.getChange(new GetChangeRequest().withId(requestId));
    }

}
