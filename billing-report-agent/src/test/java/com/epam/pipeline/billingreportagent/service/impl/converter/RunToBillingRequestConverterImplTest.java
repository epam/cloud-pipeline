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
import com.epam.pipeline.billingreportagent.model.EntityWithMetadata;
import com.epam.pipeline.billingreportagent.model.PipelineRunWithType;
import com.epam.pipeline.billingreportagent.model.ResourceType;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("checkstyle:magicnumber")
public class RunToBillingRequestConverterImplTest {

    private static final String BILLING_CENTER_KEY = "billing";
    private static final Long REGION_ID = 1L;
    private static final String NODE_TYPE = "nodetype.medium";
    private static final Long RUN_ID = 1L;
    private static final String USER_NAME = "TestUser";
    private static final String GROUP_1 = "TestGroup1";
    private static final String GROUP_2 = "TestGroup2";
    private static final Long PIPELINE_ID = 1L;
    private static final String TOOL_IMAGE = "cp/tool:latest";
    private static final BigDecimal PRICE = BigDecimal.valueOf(4, 2);
    private static final List<String> USER_GROUPS = java.util.Arrays.asList(GROUP_1, GROUP_2);
    private static final String NODE_ID = "nodeId";

    private final PipelineUser testUser = PipelineUser.builder()
        .userName(USER_NAME)
        .groups(USER_GROUPS)
        .attributes(Collections.emptyMap())
        .build();

    private final EntityWithMetadata<PipelineUser> testUserWithMetadata = EntityWithMetadata.<PipelineUser>builder()
            .entity(testUser)
            .build();

    private final RunToBillingRequestConverter converter =
        new RunToBillingRequestConverter(new RunBillingMapper(BILLING_CENTER_KEY));


    @Test
    public void shouldConvertToBillingsWithOneFinalStatus() {
        final PipelineRun run = new PipelineRun();
        run.setStartDate(Date.from(
                LocalDateTime.of(2019, 12, 4, 10, 0)
                .atZone(ZoneId.of("Z"))
                .toInstant()));
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.STOPPED, LocalDateTime.of(2019, 12, 4, 15, 0)));
        run.setRunStatuses(statuses);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, Collections.emptyList(), ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDateTime.of(2019, 12, 4, 0, 0),
                        LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(1, billings.size());
    }

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
            .entity(new PipelineRunWithType(run, Collections.emptyList(), ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 1, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(4, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(1200, reports.get(LocalDate.of(2019, 12, 1)).getCost().longValue());
        Assert.assertEquals(4800, reports.get(LocalDate.of(2019, 12, 2)).getCost().longValue());
        Assert.assertEquals(6000, reports.get(LocalDate.of(2019, 12, 3)).getCost().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
        Assert.assertEquals(180, reports.get(LocalDate.of(2019, 12, 1)).getUsageMinutes().longValue());
        Assert.assertEquals(720, reports.get(LocalDate.of(2019, 12, 2)).getUsageMinutes().longValue());
        Assert.assertEquals(900, reports.get(LocalDate.of(2019, 12, 3)).getUsageMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getUsageMinutes().longValue());
        Assert.assertEquals(540, reports.get(LocalDate.of(2019, 12, 1)).getPausedMinutes().longValue());
        Assert.assertEquals(720, reports.get(LocalDate.of(2019, 12, 2)).getPausedMinutes().longValue());
        Assert.assertEquals(540, reports.get(LocalDate.of(2019, 12, 3)).getPausedMinutes().longValue());    
        Assert.assertEquals(900, reports.get(LocalDate.of(2019, 12, 4)).getPausedMinutes().longValue());    
    }

    @Test
    public void convertRunWithNoStatusesToBilling() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
            .entity(new PipelineRunWithType(run, Collections.emptyList(), ComputeType.CPU)).build();

        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 4, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(1, billings.size());

        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(9600, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
        Assert.assertEquals(1440, reports.get(LocalDate.of(2019, 12, 4)).getUsageMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getPausedMinutes().longValue());
    }

    @Test
    public void testRunConverting() {
        final PipelineRun run = TestUtils.createTestPipelineRun(RUN_ID, PIPELINE_ID, TOOL_IMAGE, PRICE,
                                                                TestUtils.createTestInstance(REGION_ID, NODE_TYPE));

        final EntityContainer<PipelineRunWithType> runContainer =
            EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, Collections.emptyList(), ComputeType.CPU))
                .owner(testUserWithMetadata)
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
        Assert.assertEquals(run.getId().intValue(), requestFieldsMap.get("run_id"));
        Assert.assertEquals(ResourceType.COMPUTE.toString(), requestFieldsMap.get("resource_type"));
        Assert.assertEquals(run.getPipelineId().intValue(), requestFieldsMap.get("pipeline"));
        Assert.assertEquals(run.getDockerImage(), requestFieldsMap.get("tool"));
        Assert.assertEquals(run.getInstance().getNodeType(), requestFieldsMap.get("instance_type"));
        Assert.assertEquals(9600, requestFieldsMap.get("cost"));
        Assert.assertEquals(1440, requestFieldsMap.get("usage_minutes"));
        Assert.assertEquals(PRICE.unscaledValue().intValue(), requestFieldsMap.get("run_price"));
        Assert.assertEquals(run.getInstance().getCloudRegionId().intValue(), requestFieldsMap.get("cloudRegionId"));
        Assert.assertEquals(USER_NAME, requestFieldsMap.get("owner"));
        TestUtils.verifyStringArray(USER_GROUPS, requestFieldsMap.get("groups"));
    }

    @Test
    void testConvertNewFashionedRunToBillings() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 13, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 18, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 20, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 22, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 23, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 22, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 24, 15, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 25, 15, 0)));
        run.setRunStatuses(statuses);

        final List<NodeDisk> disks = new ArrayList<>();
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 5)));
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 5)));
        disks.add(new NodeDisk(40L, NODE_ID, LocalDateTime.of(2020, 5, 21, 22, 30)));
        disks.add(new NodeDisk(60L, NODE_ID, LocalDateTime.of(2020, 5, 24, 14, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, disks, ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDateTime.of(2020, 5, 21, 0, 0),
                        LocalDateTime.of(2020, 5, 26, 0, 0));
        Assert.assertEquals(5, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(6300, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        Assert.assertEquals(21600, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        Assert.assertEquals(24000, reports.get(LocalDate.of(2020, 5, 23)).getCost().longValue());
        Assert.assertEquals(28200, reports.get(LocalDate.of(2020, 5, 24)).getCost().longValue());
        Assert.assertEquals(21000, reports.get(LocalDate.of(2020, 5, 25)).getCost().longValue());
        Assert.assertEquals(240, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        Assert.assertEquals(720, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        Assert.assertEquals(1440, reports.get(LocalDate.of(2020, 5, 23)).getUsageMinutes().longValue());
        Assert.assertEquals(900, reports.get(LocalDate.of(2020, 5, 24)).getUsageMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2020, 5, 25)).getUsageMinutes().longValue());
        Assert.assertEquals(480, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        Assert.assertEquals(720, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2020, 5, 23)).getPausedMinutes().longValue());
        Assert.assertEquals(540, reports.get(LocalDate.of(2020, 5, 24)).getPausedMinutes().longValue());
        Assert.assertEquals(900, reports.get(LocalDate.of(2020, 5, 25)).getPausedMinutes().longValue());
    }

    @Test
    void testConvertOldFashionedPausedRunToBillings() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 13, 0)));
        run.setRunStatuses(statuses);

        final List<NodeDisk> disks = new ArrayList<>();
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 0)));
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, disks, ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDateTime.of(2020, 5, 21, 0, 0),
                        LocalDateTime.of(2020, 5, 23, 0, 0));
        Assert.assertEquals(2, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(5000, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        Assert.assertEquals(9600, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        Assert.assertEquals(60, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        Assert.assertEquals(660, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        Assert.assertEquals(1440, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
    }

    @Test
    void testConvertNewFashionedRunningRunToBillings() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)));
        run.setRunStatuses(statuses);

        final List<NodeDisk> disks = new ArrayList<>();
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 0)));
        disks.add(new NodeDisk(20L, NODE_ID, LocalDateTime.of(2020, 5, 21, 12, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(new PipelineRunWithType(run, disks, ComputeType.CPU)).build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDateTime.of(2020, 5, 21, 0, 0),
                        LocalDateTime.of(2020, 5, 23, 0, 0));
        Assert.assertEquals(2, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(7200, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        Assert.assertEquals(14400, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        Assert.assertEquals(720, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        Assert.assertEquals(1440, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        Assert.assertEquals(0, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
    }
}