/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.amazonaws.services.route53.model.ChangeInfo;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.ChangeStatus;
import com.amazonaws.services.route53.model.GetChangeRequest;
import com.amazonaws.services.route53.model.GetChangeResult;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsResult;
import com.epam.pipeline.entity.cloud.InstanceDNSRecord;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordFormat;
import com.epam.pipeline.entity.cloud.InstanceDNSRecordStatus;
import com.epam.pipeline.entity.region.AwsRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Route53HelperTest {

    private static final String HOSTED_ZONE_ID = "hosted-zone";
    private static final String CHANGE_ID = "change-id";
    private static final int MAX_ATTEMPTS = 2;
    private static final InstanceDNSRecord RECORD = InstanceDNSRecord.builder()
            .dnsRecord("run.example.com")
            .target("edge.example.com")
            .format(InstanceDNSRecordFormat.ABSOLUTE)
            .build();

    private final AmazonRoute53 client = mock(AmazonRoute53.class);
    private final Route53Helper helper = spy(new Route53Helper());

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(helper, "maxAttempts", MAX_ATTEMPTS);
        ReflectionTestUtils.setField(helper, "delayInSeconds", 0);
        doReturn(client).when(helper).getRoute53Client(any());
        when(client.listResourceRecordSets(any(ListResourceRecordSetsRequest.class)))
                .thenReturn(new ListResourceRecordSetsResult());
        when(client.changeResourceRecordSets(any(ChangeResourceRecordSetsRequest.class)))
                .thenReturn(new ChangeResourceRecordSetsResult().withChangeInfo(changeInfo(ChangeStatus.PENDING)));
    }

    @Test
    public void createDNSRecordShouldReturnInSyncRecordWhenWaiterHasSeenInSync() {
        when(client.getChange(any(GetChangeRequest.class)))
                .thenReturn(changeResult(ChangeStatus.PENDING),
                        changeResult(ChangeStatus.INSYNC),
                        changeResult(ChangeStatus.PENDING));

        final InstanceDNSRecord record = helper.createDNSRecord(new AwsRegion(), HOSTED_ZONE_ID, RECORD);

        assertThat(record.getStatus(), is(InstanceDNSRecordStatus.INSYNC));
        verify(client, times(2)).getChange(any(GetChangeRequest.class));
    }

    @Test
    public void createDNSRecordShouldFailWithTimeoutMessageWhenChangeStaysPending() {
        when(client.getChange(any(GetChangeRequest.class))).thenReturn(changeResult(ChangeStatus.PENDING));

        assertThrows(e -> e instanceof IllegalStateException
                        && e.getMessage().startsWith(String.format(
                                "Route53 DNS record change %s did not reach INSYNC status within %d attempts",
                                CHANGE_ID, MAX_ATTEMPTS)),
            () -> helper.createDNSRecord(new AwsRegion(), HOSTED_ZONE_ID, RECORD));
        verify(client, times(MAX_ATTEMPTS + 1)).getChange(any(GetChangeRequest.class));
    }

    private static ChangeInfo changeInfo(final ChangeStatus status) {
        return new ChangeInfo().withId(CHANGE_ID).withStatus(status);
    }

    private static GetChangeResult changeResult(final ChangeStatus status) {
        return new GetChangeResult().withChangeInfo(changeInfo(status));
    }
}
