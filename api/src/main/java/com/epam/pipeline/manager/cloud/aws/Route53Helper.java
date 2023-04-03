/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.ChangeStatus;
import com.amazonaws.services.route53.model.GetChangeRequest;
import com.amazonaws.services.route53.model.GetChangeResult;
import com.amazonaws.services.route53.model.InvalidChangeBatchException;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.amazonaws.services.route53.waiters.AmazonRoute53Waiters;
import com.amazonaws.waiters.FixedDelayStrategy;
import com.amazonaws.waiters.MaxAttemptsRetryStrategy;
import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.WaiterParameters;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordFormat;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@RequiredArgsConstructor
public class Route53Helper {

    private static final long TTL_TIME = 60L;
    private static final int MAX_ATTEMPTS = 100;
    private static final int DELAY_IN_SECONDS = 1;

    public InstanceDNSRecord createDNSRecord(final AwsRegion awsRegion,
                                             final String hostedZoneId,
                                             final InstanceDNSRecord record) {
        validate(hostedZoneId);
        validate(record);
        log.info("Creating DNS record ({}): {} -> {}", hostedZoneId, record.getDnsRecord(), record.getTarget());
        final AmazonRoute53 client = getRoute53Client(awsRegion);
        if (isDnsRecordExists(hostedZoneId, record, client)) {
            log.info("DNS record already exists ({}): {} ({}) -> {}",
                    hostedZoneId, record.getDnsRecord(), getRRType(record.getTarget()), record.getTarget());
            return record.toBuilder().status(InstanceDNSRecordStatus.INSYNC).build();
        }
        try {
            final ChangeResourceRecordSetsResult result = performChangeRequest(hostedZoneId,
                    record.getDnsRecord(), record.getTarget(), client, ChangeAction.CREATE, true);
            return record.toBuilder().status(getStatus(result.getChangeInfo().getStatus())).build();
        } catch (InvalidChangeBatchException e) {
            final String message = e.getLocalizedMessage();
            log.error("AWS 53 Route service responded with: {}", message);
            if (message.matches(".*Tried to create resource record set.*but it already exists.*")) {
                log.info("DNS Record already exists, API will proceed with this record.");
                return record.toBuilder().status(InstanceDNSRecordStatus.INSYNC).build();
            }
            throw e;
        }
    }

    public InstanceDNSRecord removeDNSRecord(final AwsRegion awsRegion,
                                             final String hostedZoneId,
                                             final InstanceDNSRecord record) {
        validate(hostedZoneId);
        validate(record);
        log.info("Removing DNS record ({}): {} -> {}", hostedZoneId, record.getDnsRecord(), record.getTarget());
        final AmazonRoute53 client = getRoute53Client(awsRegion);
        if (!isDnsRecordExists(hostedZoneId, record, client)) {
            log.info("DNS record doesn't exist ({}): {} ({}) -> {}",
                    hostedZoneId, record.getDnsRecord(), getRRType(record.getTarget()), record.getTarget());
            return record.toBuilder().status(InstanceDNSRecordStatus.NOOP).build();
        }
        final ChangeResourceRecordSetsResult result = performChangeRequest(hostedZoneId,
                record.getDnsRecord(), record.getTarget(), client, ChangeAction.DELETE, false);
        return record.toBuilder().status(getStatus(result.getChangeInfo().getStatus())).build();
    }

    private void validate(final String hostedZoneId) {
        Assert.notNull(hostedZoneId, "hostedZoneId cannot be null");
    }

    private void validate(final InstanceDNSRecord record) {
        Assert.notNull(record, "DNS record is missing");
        Assert.isTrue(StringUtils.isNotBlank(record.getDnsRecord()), "DNS record source is missing");
        Assert.isTrue(StringUtils.isNotBlank(record.getTarget()), "DNS record target is missing");
        Assert.notNull(record.getFormat(), "DNS record size is missing");
        Assert.isTrue(record.getFormat() == InstanceDNSRecordFormat.ABSOLUTE, "DNS record should be absolute");
    }

    private AmazonRoute53 getRoute53Client(final AwsRegion awsRegion) {
        return AmazonRoute53AsyncClientBuilder
                .standard()
                .withCredentials(AWSUtils.getCredentialsProvider(awsRegion))
                .build();
    }

    private boolean isDnsRecordExists(final String hostedZoneId, final InstanceDNSRecord dnsRecord,
                                      final AmazonRoute53 client) {
        return client.listResourceRecordSets(new ListResourceRecordSetsRequest()
                .withHostedZoneId(hostedZoneId)
                .withStartRecordName(dnsRecord.getDnsRecord())
                .withStartRecordType(getRRType(dnsRecord.getTarget()))).getResourceRecordSets().stream()
                .map(ResourceRecordSet::getName)
                .anyMatch(resourceRecord -> resourceRecord.equalsIgnoreCase(dnsRecord.getDnsRecord())
                        || resourceRecord.equalsIgnoreCase(dnsRecord.getDnsRecord() + "."));
    }

    private InstanceDNSRecordStatus getStatus(final String status) {
        switch (status) {
            case "PENDING": return InstanceDNSRecordStatus.PENDING;
            case "INSYNC": return InstanceDNSRecordStatus.INSYNC;
            default: return InstanceDNSRecordStatus.NOOP;
        }
    }

    private ChangeResourceRecordSetsResult performChangeRequest(final String hostedZoneId, final String dnsRecord,
                                                                final String target, final AmazonRoute53 client,
                                                                final ChangeAction action, final boolean await) {
        final ChangeResourceRecordSetsResult result = client.changeResourceRecordSets(
                new ChangeResourceRecordSetsRequest()
                    .withHostedZoneId(hostedZoneId)
                    .withChangeBatch(new ChangeBatch()
                            .withChanges(
                                    new Change()
                                            .withAction(action)
                                            .withResourceRecordSet(
                                                    new ResourceRecordSet()
                                                            .withName(dnsRecord)
                                                            .withType(getRRType(target))
                                                            .withTTL(TTL_TIME)
                                                            .withResourceRecords(
                                                                    new ResourceRecord().withValue(target)
                                                            )
                                            )
                            )
                    )
        );

        if (await) {
            final WaiterParameters<GetChangeRequest> request = new WaiterParameters<GetChangeRequest>()
                    .withPollingStrategy(
                            new PollingStrategy(
                                    new MaxAttemptsRetryStrategy(MAX_ATTEMPTS),
                                    new FixedDelayStrategy(DELAY_IN_SECONDS)
                            )
                    ).withRequest(new GetChangeRequest().withId(result.getChangeInfo().getId()));
            new AmazonRoute53Waiters(client).resourceRecordSetsChanged().run(request);
            final String status = checkRequestStatus(client, request.getRequest().getId()).getChangeInfo().getStatus();
            if (status.equalsIgnoreCase(ChangeStatus.INSYNC.name())) {
                result.getChangeInfo().setStatus(status);
            } else {
                throw new IllegalStateException("Can't create Route53 DNS record for some reason.");
            }
        }

        return result;
    }

    private static RRType getRRType(final String target) {
        if (target.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return RRType.A;
        } else {
            return RRType.CNAME;
        }
    }

    private GetChangeResult checkRequestStatus(final AmazonRoute53 client, final String requestId) {
        return client.getChange(new GetChangeRequest().withId(requestId));
    }

}
