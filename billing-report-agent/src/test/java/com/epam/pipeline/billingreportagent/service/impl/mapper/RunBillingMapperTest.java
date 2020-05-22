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

package com.epam.pipeline.billingreportagent.service.impl.mapper;

import com.epam.pipeline.billingreportagent.model.ComputeType;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RunBillingMapperTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final Long TEST_PIPELINE_ID = 1L;
    private static final String TEST_USER_NAME = "User";
    private static final String TEST_TOOL_IMAGE = "cp/tool:latest";
    private static final String TEST_NODE_TYPE = "nodetype.medium";
    private static final String TEST_GROUP_1 = "TestGroup1";
    private static final String TEST_GROUP_2 = "TestGroup2";
    private static final Long TEST_COST = 10L;
    private static final Long TEST_RUN_ID = 1L;
    private static final Long TEST_REGION_ID = 1L;
    private static final BigDecimal TEST_PRICE = BigDecimal.ONE;
    private static final Long TEST_USAGE_MINUTES = 600L;
    private static final List<String> TEST_GROUPS = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);
    private static final LocalDate TEST_DATE = LocalDate.now();

    private final RunBillingMapper mapper = new RunBillingMapper(BILLING_CENTER_KEY);
    private final PipelineUser testUser = PipelineUser.builder()
        .userName(TEST_USER_NAME)
        .groups(TEST_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final EntityWithMetadata<PipelineUser> testUserWithMetadata = EntityWithMetadata.<PipelineUser>builder()
            .entity(testUser)
            .build();

    @Test
    public void testRunMapperMap() throws IOException {
        final PipelineRun run =
            TestUtils.createTestPipelineRun(TEST_RUN_ID, TEST_PIPELINE_ID, TEST_TOOL_IMAGE, TEST_PRICE,
                                            TestUtils.createTestInstance(TEST_REGION_ID, TEST_NODE_TYPE));
        final PipelineRunBillingInfo billing = PipelineRunBillingInfo.builder()
            .run(new PipelineRunWithType(run, Collections.emptyList(), ComputeType.CPU))
            .date(TEST_DATE)
            .cost(TEST_COST)
            .usageMinutes(TEST_USAGE_MINUTES)
            .build();
        final EntityContainer<PipelineRunBillingInfo> billingContainer =
            EntityContainer.<PipelineRunBillingInfo>builder()
            .entity(billing)
            .owner(testUserWithMetadata)
            .build();

        final XContentBuilder mappedBilling = mapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(TEST_RUN_ID.intValue(), mappedFields.get("run_id"));
        Assert.assertEquals(ResourceType.COMPUTE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals(TEST_PIPELINE_ID.intValue(), mappedFields.get("pipeline"));
        Assert.assertEquals(TEST_TOOL_IMAGE, mappedFields.get("tool"));
        Assert.assertEquals(TEST_NODE_TYPE, mappedFields.get("instance_type"));
        Assert.assertEquals(TEST_COST.intValue(), mappedFields.get("cost"));
        Assert.assertEquals(TEST_USAGE_MINUTES.intValue(), mappedFields.get("usage_minutes"));
        Assert.assertEquals(run.getPricePerHour().intValue(), mappedFields.get("run_price"));
        Assert.assertEquals(TEST_REGION_ID.intValue(), mappedFields.get("cloudRegionId"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }
}
