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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.ComputeType;
import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("checkstyle:magicnumber")
public class RunToBillingRequestConverterImplTest {

    private static final long REGION_ID = 1;
    private static final String NODE_TYPE = "nodetype.medium";
    private static final Long RUN_ID = 1L;
    private static final String USER_NAME = "TestUser";
    private static final String GROUP_1 = "TestGroup1";
    private static final String GROUP_2 = "TestGroup2";
    private static final String PIPELINE_NAME = "TestPipeline";
    private static final String TOOL_IMAGE = "cp/tool:latest";
    private static final BigDecimal PRICE = BigDecimal.valueOf(4, 2);
    private static final List<String> USER_GROUPS = java.util.Arrays.asList(GROUP_1, GROUP_2);

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(USER_NAME)
        .groups(USER_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final RunToBillingRequestConverter converter =
        new RunToBillingRequestConverter(new RunBillingMapper());

    @Test
    public void convertRunToBillings() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 1, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 1, 13, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 1, 18, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 1, 20, 0)));

        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 2, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 3, 15, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.STOPPED, LocalDateTime.of(2019, 12, 4, 15, 0)));
        run.setRunStatuses(statuses);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
            .entity(new PipelineRunWithType(run, ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 1, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(3, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(1200, reports.get(LocalDate.of(2019, 12, 1)).getCost().longValue());
        Assert.assertEquals(4800, reports.get(LocalDate.of(2019, 12, 2)).getCost().longValue());
        Assert.assertEquals(6000, reports.get(LocalDate.of(2019, 12, 3)).getCost().longValue());
    }

    @Test
    public void convertRunWithNoStatusesToBilling() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
            .entity(new PipelineRunWithType(run, ComputeType.CPU)).build();

        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 4, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(1, billings.size());

        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(9600, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
    }

    @Test
    public void testRunConverting() {
        final PipelineRun run = TestUtils.createTestPipelineRun(RUN_ID, PIPELINE_NAME, TOOL_IMAGE, PRICE,
                                                                TestUtils.createTestInstance(REGION_ID, NODE_TYPE));

        final EntityContainer<PipelineRunWithType> runContainer =
            EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, ComputeType.CPU))
                .owner(testUser)
                .build();

        final LocalDateTime prevSync = LocalDateTime.of(2019, 12, 4, 0, 0);
        final LocalDateTime syncStart = LocalDateTime.of(2019, 12, 5, 0, 0);
        final List<DocWriteRequest> billings =
            converter.convertEntityToRequests(runContainer, TestUtils.RUN_BILLING_PREFIX, prevSync, syncStart);
        Assert.assertEquals(1, billings.size());

        final DocWriteRequest billing = billings.get(0);
        final Map<String, Object> requestFieldsMap = ((IndexRequest) billing).sourceAsMap();
        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.RUN_BILLING_PREFIX, prevSync);
        Assert.assertEquals(expectedIndex, billing.index());
        Assert.assertEquals(run.getId().intValue(), requestFieldsMap.get("id"));
        Assert.assertEquals(ResourceType.COMPUTE.toString(), requestFieldsMap.get("resource_type"));
        Assert.assertEquals(run.getPipelineName(), requestFieldsMap.get("pipeline"));
        Assert.assertEquals(run.getDockerImage(), requestFieldsMap.get("tool"));
        Assert.assertEquals(run.getInstance().getNodeType(), requestFieldsMap.get("instance_type"));
        Assert.assertEquals(9600, requestFieldsMap.get("cost"));
        Assert.assertEquals(1441, requestFieldsMap.get("usage"));
        Assert.assertEquals(PRICE.unscaledValue().intValue(), requestFieldsMap.get("run_price"));
        Assert.assertEquals(run.getInstance().getCloudRegionId().intValue(), requestFieldsMap.get("cloudRegionId"));
        Assert.assertEquals(USER_NAME, requestFieldsMap.get("owner"));
        TestUtils.verifyStringArray(USER_GROUPS, requestFieldsMap.get("groups"));
    }
}