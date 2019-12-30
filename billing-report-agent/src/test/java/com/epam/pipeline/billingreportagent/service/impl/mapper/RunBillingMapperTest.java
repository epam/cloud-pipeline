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

package com.epam.pipeline.billingreportagent.service.impl.mapper;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
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
import java.util.List;
import java.util.Map;

public class RunBillingMapperTest {

    private static final String TEST_PIPELINE = "TestPipeline";
    private static final String TEST_USER_NAME = "User";
    private static final String TEST_TOOL_IMAGE = "cp/tool:latest";
    private static final String TEST_NODE_TYPE = "nodetype.medium";
    private static final String TEST_GROUP_1 = "TestGroup1";
    private static final String TEST_GROUP_2 = "TestGroup2";
    private static final long TEST_COST = 10;
    private static final long TEST_RUN_ID = 1;
    private static final long TEST_REGION_ID = 1;
    private static final BigDecimal TEST_PRICE = BigDecimal.ONE;
    private static final long TEST_USAGE_MINUTES = 600;
    private static final List<String> TEST_GROUPS = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);
    private static final LocalDate TEST_DATE = LocalDate.now();

    private final RunBillingMapper mapper = new RunBillingMapper();
    private final PipelineUser testUser = PipelineUser.builder()
        .userName(TEST_USER_NAME)
        .groups(TEST_GROUPS)
        .build();

    @Test
    public void testRunMapperMap() throws IOException {
        final PipelineRun run =
            TestUtils.createTestPipelineRun(TEST_RUN_ID, TEST_PIPELINE, TEST_TOOL_IMAGE, TEST_PRICE,
                                            TestUtils.createTestInstance(TEST_REGION_ID, TEST_NODE_TYPE));
        final PipelineRunBillingInfo billing = PipelineRunBillingInfo.builder()
            .run(run)
            .date(TEST_DATE)
            .cost(TEST_COST)
            .usageMinutes(TEST_USAGE_MINUTES)
            .build();
        final EntityContainer<PipelineRunBillingInfo> billingContainer =
            EntityContainer.<PipelineRunBillingInfo>builder()
            .entity(billing)
            .owner(testUser)
            .build();

        final XContentBuilder mappedBilling = mapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(run.getId().intValue(), mappedFields.get("id"));
        Assert.assertEquals(ResourceType.COMPUTE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals(run.getPipelineName(), mappedFields.get("pipeline"));
        Assert.assertEquals(run.getDockerImage(), mappedFields.get("tool"));
        Assert.assertEquals(run.getInstance().getNodeType(), mappedFields.get("instance_type"));
        Assert.assertEquals(billing.getCost().intValue(), mappedFields.get("cost"));
        Assert.assertEquals(billing.getUsageMinutes().intValue(), mappedFields.get("usage"));
        Assert.assertEquals(run.getPricePerHour().intValue(), mappedFields.get("run_price"));
        Assert.assertEquals(run.getInstance().getCloudRegionId().intValue(), mappedFields.get("cloudRegionId"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }
}
