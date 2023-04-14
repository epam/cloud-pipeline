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
import com.epam.pipeline.billingreportagent.model.ToolAddress;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.AbstractEntityMapper;
import com.epam.pipeline.billingreportagent.service.ElasticsearchSynchronizer;
import com.epam.pipeline.billingreportagent.service.EntityToBillingRequestConverter;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.search.SearchDocumentType;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class RunBillingMapperTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final Long TEST_PIPELINE_ID = 1L;
    private static final String TEST_PIPELINE_NAME = "pipeline_name";
    private static final String TEST_PIPELINE_VERSION = "pipeline_version";
    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_USER_NAME = "User";
    private static final String TEST_TOOL_IMAGE = "cp/tool:latest";
    private static final Long TEST_TOOL_REGISTRY_ID = 1L;
    private static final String TEST_TOOL_REGISTRY_NAME = "registry_name";
    private static final Long TEST_TOOL_GROUP_ID = 1L;
    private static final String TEST_TOOL_GROUP_NAME = "group_name";
    private static final Long TEST_TOOL_ID = 1L;
    private static final String TEST_TOOL_NAME = "tool_name";
    private static final String TEST_TOOL_VERSION = "tool_version";
    private static final String TEST_NODE_TYPE = "nodetype.medium";
    private static final String TEST_GROUP_1 = "TestGroup1";
    private static final String TEST_GROUP_2 = "TestGroup2";
    private static final Long TEST_COST = 10L;
    private static final Long TEST_RUN_ID = 1L;
    private static final ComputeType TEST_RUN_COMPUTE_TYPE = ComputeType.CPU;
    private static final Long TEST_REGION_ID = 1L;
    private static final String TEST_REGION_NAME = "test-region";
    private static final CloudProvider TEST_REGION_PROVIDER = CloudProvider.AWS;
    private static final BigDecimal TEST_PRICE = BigDecimal.ONE;
    private static final int TEST_PRICES_MULTIPLIER = 100_000;
    private static final Long TEST_USAGE_MINUTES = 600L;
    private static final Long TEST_PAUSED_MINUTES = 400L;
    private static final List<String> TEST_GROUPS = Arrays.asList(TEST_GROUP_1, TEST_GROUP_2);

    private static final Date TEST_JAVA_DATE = DateUtils.now();
    private static final LocalDate TEST_DATE = DateUtils.convertDateToLocalDateTime(TEST_JAVA_DATE).toLocalDate();
    private static final Date TEST_STARTED_DATE = toDate(TEST_DATE.atStartOfDay());
    private static final String TEST_STARTED_DATE_STR = toString(TEST_STARTED_DATE);
    private static final Date TEST_FINISHED_DATE = toDate(TEST_DATE.plusDays(1L).atStartOfDay());
    private static final String TEST_FINISHED_DATE_STR = toString(TEST_FINISHED_DATE);

    private static Date toDate(final LocalDateTime localDateTime) {
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    private static String toString(final Date date) {
        return new SimpleDateFormat(Constants.FMT_ISO_LOCAL_DATE).format(date);
    }

    private final RunBillingMapper mapper = new RunBillingMapper(BILLING_CENTER_KEY);
    private final PipelineUser testUser = PipelineUser.builder()
        .id(TEST_USER_ID)
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
        run.setPipelineId(TEST_PIPELINE_ID);
        run.setPipelineName(TEST_PIPELINE_NAME);
        run.setVersion(TEST_PIPELINE_VERSION);
        run.setStartDate(TEST_STARTED_DATE);
        run.setEndDate(TEST_FINISHED_DATE);
        run.setPricePerHour(TEST_PRICE);
        run.setComputePricePerHour(TEST_PRICE);
        run.setDiskPricePerHour(TEST_PRICE);
        final Tool tool = new Tool();
        tool.setId(TEST_TOOL_ID);
        tool.setRegistryId(TEST_TOOL_REGISTRY_ID);
        tool.setRegistry(TEST_TOOL_REGISTRY_NAME);
        tool.setToolGroupId(TEST_TOOL_GROUP_ID);
        tool.setToolGroup(TEST_TOOL_GROUP_NAME);
        tool.setCreatedDate(TEST_JAVA_DATE);
        final EntityContainer<Tool> toolEntity = EntityContainer.<Tool>builder()
                .entity(tool)
                .owner(testUserWithMetadata)
                .build();
        final Pipeline pipeline = new Pipeline();
        pipeline.setId(TEST_PIPELINE_ID);
        pipeline.setName(TEST_PIPELINE_NAME);
        pipeline.setCreatedDate(TEST_JAVA_DATE);
        final EntityContainer<Pipeline> pipelineEntity = EntityContainer.<Pipeline>builder()
                .entity(pipeline)
                .owner(testUserWithMetadata)
                .build();
        final PipelineRunBillingInfo billing = PipelineRunBillingInfo.builder()
            .run(new PipelineRunWithType(run,
                    ToolAddress.from(TEST_TOOL_REGISTRY_NAME + "/"
                            + TEST_TOOL_GROUP_NAME + "/"
                            + TEST_TOOL_NAME + ":" + TEST_TOOL_VERSION),
                    toolEntity, pipelineEntity, Collections.emptyList(),
                    TEST_RUN_COMPUTE_TYPE))
            .date(TEST_DATE)
            .cost(TEST_COST)
            .usageMinutes(TEST_USAGE_MINUTES)
            .pausedMinutes(TEST_PAUSED_MINUTES)
            .build();
        final AbstractCloudRegion region = new AwsRegion();
        region.setId(TEST_REGION_ID);
        region.setName(TEST_REGION_NAME);
        final EntityContainer<PipelineRunBillingInfo> billingContainer =
            EntityContainer.<PipelineRunBillingInfo>builder()
            .entity(billing)
            .owner(testUserWithMetadata)
            .region(region)
            .build();

        final XContentBuilder mappedBilling = mapper.map(billingContainer);

        final Map<String, Object> mappedFields = TestUtils.getPuttedObject(mappedBilling);

        Assert.assertEquals(SearchDocumentType.PIPELINE_RUN.name(),
                mappedFields.get(ElasticsearchSynchronizer.DOC_TYPE_FIELD));
        Assert.assertEquals(EntityToBillingRequestConverter.SIMPLE_DATE_FORMAT.format(TEST_DATE),
                mappedFields.get("created_date"));
        Assert.assertEquals(ResourceType.COMPUTE.toString(), mappedFields.get("resource_type"));
        Assert.assertEquals(TEST_REGION_ID.intValue(), mappedFields.get("cloudRegionId"));
        Assert.assertEquals(TEST_REGION_NAME, mappedFields.get("cloud_region_name"));
        Assert.assertEquals(TEST_REGION_PROVIDER.toString(), mappedFields.get("cloud_region_provider"));

        Assert.assertEquals(TEST_RUN_ID.intValue(), mappedFields.get("run_id"));
        Assert.assertEquals(TEST_RUN_COMPUTE_TYPE.toString(), mappedFields.get("compute_type"));
        Assert.assertEquals(TEST_NODE_TYPE, mappedFields.get("instance_type"));

        Assert.assertEquals(TEST_PIPELINE_ID.intValue(), mappedFields.get("pipeline"));
        Assert.assertEquals(TEST_PIPELINE_NAME, mappedFields.get("pipeline_name"));
        Assert.assertEquals(TEST_PIPELINE_VERSION, mappedFields.get("pipeline_version"));
        Assert.assertEquals(TEST_USER_ID.intValue(), mappedFields.get("pipeline_owner_id"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("pipeline_owner_name"));
        Assert.assertEquals(AbstractEntityMapper.SIMPLE_DATE_FORMAT.format(TEST_JAVA_DATE),
                mappedFields.get("pipeline_created_date"));

        Assert.assertEquals(TEST_TOOL_IMAGE, mappedFields.get("tool"));
        Assert.assertEquals(TEST_TOOL_REGISTRY_ID.intValue(), mappedFields.get("tool_registry_id"));
        Assert.assertEquals(TEST_TOOL_REGISTRY_NAME, mappedFields.get("tool_registry_name"));
        Assert.assertEquals(TEST_TOOL_GROUP_ID.intValue(), mappedFields.get("tool_group_id"));
        Assert.assertEquals(TEST_TOOL_GROUP_NAME, mappedFields.get("tool_group_name"));
        Assert.assertEquals(TEST_TOOL_ID.intValue(), mappedFields.get("tool_id"));
        Assert.assertEquals(TEST_TOOL_NAME, mappedFields.get("tool_name"));
        Assert.assertEquals(TEST_TOOL_VERSION, mappedFields.get("tool_version"));
        Assert.assertEquals(TEST_USER_ID.intValue(), mappedFields.get("tool_owner_id"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("tool_owner_name"));
        Assert.assertEquals(AbstractEntityMapper.SIMPLE_DATE_FORMAT.format(TEST_JAVA_DATE),
                mappedFields.get("tool_created_date"));

        Assert.assertEquals(TEST_USAGE_MINUTES.intValue(), mappedFields.get("usage_minutes"));
        Assert.assertEquals(TEST_PAUSED_MINUTES.intValue(), mappedFields.get("paused_minutes"));
        Assert.assertEquals(run.getPricePerHour().intValue(), mappedFields.get("run_price"));
        Assert.assertEquals(run.getComputePricePerHour().intValue() * TEST_PRICES_MULTIPLIER,
                mappedFields.get("compute_price"));
        Assert.assertEquals(run.getDiskPricePerHour().intValue() * TEST_PRICES_MULTIPLIER,
                mappedFields.get("disk_price"));
        Assert.assertEquals(TEST_COST.intValue(), mappedFields.get("cost"));

        Assert.assertEquals(TEST_STARTED_DATE_STR, mappedFields.get("started_date"));
        Assert.assertEquals(TEST_FINISHED_DATE_STR, mappedFields.get("finished_date"));

        Assert.assertEquals(TEST_USER_ID.intValue(), mappedFields.get("owner_id"));
        Assert.assertEquals(TEST_USER_NAME, mappedFields.get("owner"));
        TestUtils.verifyStringArray(TEST_GROUPS, mappedFields.get("groups"));
    }

}
