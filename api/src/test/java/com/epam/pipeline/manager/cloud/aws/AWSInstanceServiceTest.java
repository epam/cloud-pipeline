/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.epam.pipeline.entity.cloud.CloudInstanceState;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.exception.cloud.aws.AwsEc2Exception;
import com.epam.pipeline.manager.cloud.CommonCloudInstanceService;
import com.epam.pipeline.manager.cloud.commands.ClusterCommandService;
import com.epam.pipeline.manager.cluster.InstanceOfferManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import org.junit.jupiter.api.Test;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Covers only {@link AWSInstanceService#getInstanceState}: a missing instance (gone for good) must be
 * reported as {@link CloudInstanceState#TERMINATED}, while any other AWS SDK error must propagate, so the
 * caller (unavailable-node handling) does not mistake a transient cloud error for a terminated instance.
 */
public class AWSInstanceServiceTest {

    private static final String NODE_LABEL = "1";

    private final EC2Helper ec2Helper = mock(EC2Helper.class);
    private final AwsRegion region = new AwsRegion();
    private final AWSInstanceService instanceService = new AWSInstanceService(ec2Helper,
            mock(PreferenceManager.class), mock(InstanceOfferManager.class), mock(CommonCloudInstanceService.class),
            mock(ClusterCommandService.class), mock(Route53Helper.class), mock(AWSCredentialsService.class),
            "nodeup", "nodedown", "reassign", "terminate");

    @Test
    public void shouldReturnRunningForAPendingOrRunningInstance() {
        doReturn(instanceWithState(InstanceStateName.Running)).when(ec2Helper)
                .getAliveInstance(NODE_LABEL, region);

        assertThat(instanceService.getInstanceState(region, NODE_LABEL)).isEqualTo(CloudInstanceState.RUNNING);
    }

    @Test
    public void shouldReturnTerminatedWhenNoInstanceMatchesTheNameTag() {
        doThrow(new AwsEc2Exception("No reservations found with name tag '" + NODE_LABEL + "'"))
                .when(ec2Helper).getAliveInstance(NODE_LABEL, region);

        assertThat(instanceService.getInstanceState(region, NODE_LABEL)).isEqualTo(CloudInstanceState.TERMINATED);
    }

    @Test
    public void shouldPropagateATransientCloudErrorInsteadOfReportingTerminated() {
        doThrow(new AmazonServiceException("Request limit exceeded"))
                .when(ec2Helper).getAliveInstance(eq(NODE_LABEL), eq(region));

        assertThrows(AmazonServiceException.class, () -> instanceService.getInstanceState(region, NODE_LABEL));
    }

    private static Instance instanceWithState(final InstanceStateName stateName) {
        return new Instance().withState(new InstanceState().withName(stateName));
    }
}
