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
import com.epam.pipeline.billingreportagent.model.ToolAddress;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.TestUtils;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private static final List<String> USER_GROUPS = Arrays.asList(GROUP_1, GROUP_2);
    private static final String NODE_ID = "nodeId";
    private static final LocalDateTime NO_DATE = null;

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
    public void convertShouldReturnElasticRequests() {
        final LocalDateTime prevSync = LocalDate.of(2019, 12, 4).atStartOfDay();
        final LocalDateTime syncStart = LocalDate.of(2019, 12, 5).atStartOfDay();

        final PipelineRun run = TestUtils.createTestPipelineRun(RUN_ID, PIPELINE_ID, TOOL_IMAGE, PRICE, prevSync,
                TestUtils.createTestInstance(REGION_ID, NODE_TYPE));

        final EntityContainer<PipelineRunWithType> runContainer =
                EntityContainer.<PipelineRunWithType>builder()
                        .entity(runEntity(run, Collections.emptyList()))
                        .owner(testUserWithMetadata)
                        .region(TestUtils.createTestRegion(REGION_ID))
                        .build();

        final List<DocWriteRequest> billings =
                converter.convertEntityToRequests(runContainer, TestUtils.RUN_BILLING_PREFIX, prevSync, syncStart);
        assertEquals(1, billings.size());

        final DocWriteRequest billing = billings.get(0);
        final Map<String, Object> requestFieldsMap = ((IndexRequest) billing).sourceAsMap();
        final String expectedIndex = TestUtils.buildBillingIndex(TestUtils.RUN_BILLING_PREFIX, prevSync);
        assertEquals(expectedIndex, billing.index());
        assertEquals(run.getId().intValue(), requestFieldsMap.get("run_id"));
        assertEquals(ResourceType.COMPUTE.toString(), requestFieldsMap.get("resource_type"));
        assertEquals(run.getPipelineId().intValue(), requestFieldsMap.get("pipeline"));
        assertEquals(run.getDockerImage(), requestFieldsMap.get("tool"));
        assertEquals(run.getInstance().getNodeType(), requestFieldsMap.get("instance_type"));
        assertEquals(9600, requestFieldsMap.get("cost"));
        assertEquals(1440, requestFieldsMap.get("usage_minutes"));
        assertEquals(PRICE.unscaledValue().intValue(), requestFieldsMap.get("run_price"));
        assertEquals(run.getInstance().getCloudRegionId().intValue(), requestFieldsMap.get("cloudRegionId"));
        assertEquals(USER_NAME, requestFieldsMap.get("owner"));
        TestUtils.verifyStringArray(USER_GROUPS, requestFieldsMap.get("groups"));
    }

    @Test
    public void convertShouldReturnBillingsForSingleRunningPeriod() {
        final PipelineRun run = run(LocalDateTime.of(2019, 12, 4, 14, 55),
                status(TaskStatus.STOPPED, LocalDateTime.of(2019, 12, 4, 15, 0)));
        run.setStartDate(Date.from(
                LocalDateTime.of(2019, 12, 4, 10, 0)
                .atZone(ZoneId.of("Z"))
                .toInstant()));
        run.setPricePerHour(BigDecimal.valueOf(4, 2));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2019, 12, 4).atStartOfDay(),
                        LocalDate.of(2019, 12, 5).atStartOfDay());
        assertEquals(1, billings.size());
    }

    @Test
    public void convertShouldReturnBillingsForMultipleRunningAndPausedPeriods() {
        final PipelineRun run = run(LocalDateTime.of(2019, 12, 1, 11, 55),
                status(TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 1, 12, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 1, 13, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 1, 18, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 1, 20, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 2, 12, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 3, 15, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2019, 12, 4, 15, 0)));
        run.setPricePerHour(BigDecimal.valueOf(4, 2));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();
        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDate.of(2019, 12, 1).atStartOfDay(),
                                           LocalDate.of(2019, 12, 5).atStartOfDay());
        assertEquals(4, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(1200, reports.get(LocalDate.of(2019, 12, 1)).getCost().longValue());
        assertEquals(4800, reports.get(LocalDate.of(2019, 12, 2)).getCost().longValue());
        assertEquals(6000, reports.get(LocalDate.of(2019, 12, 3)).getCost().longValue());
        assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
        assertEquals(180, reports.get(LocalDate.of(2019, 12, 1)).getUsageMinutes().longValue());
        assertEquals(720, reports.get(LocalDate.of(2019, 12, 2)).getUsageMinutes().longValue());
        assertEquals(900, reports.get(LocalDate.of(2019, 12, 3)).getUsageMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getUsageMinutes().longValue());
        assertEquals(540, reports.get(LocalDate.of(2019, 12, 1)).getPausedMinutes().longValue());
        assertEquals(720, reports.get(LocalDate.of(2019, 12, 2)).getPausedMinutes().longValue());
        assertEquals(540, reports.get(LocalDate.of(2019, 12, 3)).getPausedMinutes().longValue());    
        assertEquals(900, reports.get(LocalDate.of(2019, 12, 4)).getPausedMinutes().longValue());    
    }

    @Test
    public void convertShouldReturnBillingsIfThereAreNoStatuses() {
        final PipelineRun run = run(LocalDateTime.of(2019, 12, 3, 0, 0));
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDate.of(2019, 12, 4).atStartOfDay(),
                                           LocalDate.of(2019, 12, 5).atStartOfDay());
        assertEquals(1, billings.size());

        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(9600, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
        assertEquals(1440, reports.get(LocalDate.of(2019, 12, 4)).getUsageMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2019, 12, 4)).getPausedMinutes().longValue());
    }

    @Test
    public void convertShouldReturnBillingsForNewFashionedRunForSingleRunningPeriod() {
        final PipelineRun run = run(LocalDateTime.of(2020, 5, 21, 11, 55),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)));
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));

        final List<NodeDisk> disks = new ArrayList<>();
        disks.add(disk(20L, LocalDateTime.of(2020, 5, 21, 12, 0)));
        disks.add(disk(20L, LocalDateTime.of(2020, 5, 21, 12, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, disks))
                .build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2020, 5, 21).atStartOfDay(),
                        LocalDate.of(2020, 5, 23).atStartOfDay());
        assertEquals(2, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(7200, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        assertEquals(14400, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        assertEquals(720, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        assertEquals(1440, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
    }

    @Test
    public void convertShouldReturnBillingsForNewFashionedRunForMultipleRunningAndPausedPeriods() {
        final PipelineRun run = run(LocalDateTime.of(2020, 5, 21, 11, 55),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 13, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 18, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 20, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 22, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 23, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 22, 12, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 24, 15, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 25, 15, 0)));
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));

        final List<NodeDisk> disks = Arrays.asList(
                disk(20L, LocalDateTime.of(2020, 5, 21, 12, 5)),
                disk(20L, LocalDateTime.of(2020, 5, 21, 12, 5)),
                disk(40L, LocalDateTime.of(2020, 5, 21, 22, 30)),
                disk(60L, LocalDateTime.of(2020, 5, 24, 14, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, disks))
                .build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2020, 5, 21).atStartOfDay(),
                        LocalDate.of(2020, 5, 26).atStartOfDay());
        assertEquals(5, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(6300, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        assertEquals(21600, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        assertEquals(24000, reports.get(LocalDate.of(2020, 5, 23)).getCost().longValue());
        assertEquals(28200, reports.get(LocalDate.of(2020, 5, 24)).getCost().longValue());
        assertEquals(21000, reports.get(LocalDate.of(2020, 5, 25)).getCost().longValue());
        assertEquals(240, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        assertEquals(720, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        assertEquals(1440, reports.get(LocalDate.of(2020, 5, 23)).getUsageMinutes().longValue());
        assertEquals(900, reports.get(LocalDate.of(2020, 5, 24)).getUsageMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2020, 5, 25)).getUsageMinutes().longValue());
        assertEquals(480, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        assertEquals(720, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2020, 5, 23)).getPausedMinutes().longValue());
        assertEquals(540, reports.get(LocalDate.of(2020, 5, 24)).getPausedMinutes().longValue());
        assertEquals(900, reports.get(LocalDate.of(2020, 5, 25)).getPausedMinutes().longValue());
    }

    @Test
    public void convertShouldReturnBillingsForOldFashionedPausedRun() {
        final PipelineRun run = run(LocalDateTime.of(2020, 5, 21, 11, 55),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 21, 12, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 21, 13, 0)));
        run.setPricePerHour(new BigDecimal("0.04"));
        run.setComputePricePerHour(new BigDecimal("0.02000"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));

        final List<NodeDisk> disks = new ArrayList<>();
        disks.add(disk(20L, LocalDateTime.of(2020, 5, 21, 12, 0)));
        disks.add(disk(20L, LocalDateTime.of(2020, 5, 21, 12, 0)));
        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, disks))
                .build();
        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2020, 5, 21).atStartOfDay(),
                        LocalDate.of(2020, 5, 23).atStartOfDay());
        assertEquals(2, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
                billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(5000, reports.get(LocalDate.of(2020, 5, 21)).getCost().longValue());
        assertEquals(9600, reports.get(LocalDate.of(2020, 5, 22)).getCost().longValue());
        assertEquals(60, reports.get(LocalDate.of(2020, 5, 21)).getUsageMinutes().longValue());
        assertEquals(0, reports.get(LocalDate.of(2020, 5, 22)).getUsageMinutes().longValue());
        assertEquals(660, reports.get(LocalDate.of(2020, 5, 21)).getPausedMinutes().longValue());
        assertEquals(1440, reports.get(LocalDate.of(2020, 5, 22)).getPausedMinutes().longValue());
    }

    @Test
    public void testAdjustStatusesForStartedStatusRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedStatusAndEarlyStartedDateRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 11, 50, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedStatusAndNoStartedDateRun() {
        final PipelineRun run = run(
                NO_DATE,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndNoStartedDateRun() {
        final PipelineRun run = run(
                NO_DATE);

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndPreviouslyStoppedDateRun() {
        final PipelineRun run = run(
                NO_DATE,
                LocalDateTime.of(2020, 5, 19, 14, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedStoppedStatusesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedStatusAndStoppedDateRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                LocalDateTime.of(2020, 5, 20, 14, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedPausedStatusesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedPausedResumedStatusesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 16, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 16, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForStartedPausedStoppedStatusesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 18, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 18, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForPreviouslyStartedAndStoppedStatusesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 15, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 15, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForPreviouslyStartedStoppedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 19, 13, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForPreviouslyStoppedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                LocalDateTime.of(2020, 5, 19, 13, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 19, 13, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForPreviouslyStartedPausedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 19, 20, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForPreviouslyStartedPausedAndResumedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 19, 20, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }
    
    @Test
    public void testAdjustStatusesForPreviouslyStartedPausedResumedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 11, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 19, 20, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 22, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }
    
    @Test
    public void testAdjustStatusesForStartedAndIntermediatelyPausedResumedRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)),
                status(TaskStatus.PAUSING, LocalDateTime.of(2020, 5, 20, 12, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)),
                status(TaskStatus.RESUMING, LocalDateTime.of(2020, 5, 20, 13, 55, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.PAUSING, LocalDateTime.of(2020, 5, 20, 12, 55, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)),
                status(TaskStatus.RESUMING, LocalDateTime.of(2020, 5, 20, 13, 55, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesBoundsSyntheticFirstRunningStatusToRequestedInterval() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesBoundsSyntheticFirstRunningStatusToStartedDate() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2020, 5, 20, 13, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesBoundsSyntheticLastStoppedStatusToRequestedInterval() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }
    
    @Test
    public void testAdjustStatusesBoundsSyntheticLastStoppedStatusToStoppedDate() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                LocalDateTime.of(2020, 5, 20, 16, 0, 0),
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 11, 55, 0)));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 16, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndPreviouslyStartedRun() {
        final PipelineRun run = run(LocalDateTime.of(2020, 5, 19, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndStartedDateRun() {
        final PipelineRun run = run(LocalDateTime.of(2020, 5, 20, 12, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndStartedStoppedDatesRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 20, 12, 0, 0),
                LocalDateTime.of(2020, 5, 20, 14, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 14, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndAfterStartedDateRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 21, 12, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndNoDatesRun() {
        final PipelineRun run = run(
                NO_DATE);

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 20, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesForNoStatusesAndPreviouslyStartedDateRun() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 20, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesDoNotFailWithoutSyncStartDate() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                NO_DATE,
                LocalDate.of(2020, 5, 21).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2020, 5, 19, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2020, 5, 21, 0, 0, 0)));
    }

    @Test
    public void testAdjustStatusesFailWithoutSyncEndDate() {
        final PipelineRun run = run(
                LocalDateTime.of(2020, 5, 19, 12, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> converter.adjustStatuses(run,
                LocalDate.of(2020, 5, 20).atStartOfDay(),
                NO_DATE));
    }

    private void assertRunsActivityStats(final List<RunStatus> adjustedStatuses, final RunStatus... statuses) {
        assertThat(adjustedStatuses.size(), is(statuses.length));
        for (int i = 0; i < statuses.length; i++) {
            assertThat(adjustedStatuses.get(i).getStatus(), is(statuses[i].getStatus()));
            assertThat(adjustedStatuses.get(i).getTimestamp(), is(statuses[i].getTimestamp()));
        }
    }

    private PipelineRun run(final LocalDateTime start, final RunStatus... statuses) {
        return run(start, NO_DATE, statuses);
    }

    private PipelineRun run(final LocalDateTime start, final LocalDateTime end, final RunStatus... statuses) {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setStatus(end == null ? TaskStatus.RUNNING : TaskStatus.STOPPED);
        run.setRunStatuses(Arrays.asList(statuses));
        run.setInstanceStartDate(toDate(start));
        run.setEndDate(toDate(end));
        return run;
    }

    private Date toDate(final LocalDateTime date) {
        return Optional.ofNullable(date).map(DateUtils::convertLocalDateTimeToDate).orElse(null);
    }

    private NodeDisk disk(final long size, final LocalDateTime date) {
        return new NodeDisk(size, NODE_ID, date);
    }

    private RunStatus status(final TaskStatus status, final LocalDateTime date) {
        return new RunStatus(RUN_ID, status, date);
    }

    private PipelineRunWithType runEntity(final PipelineRun run, final List<NodeDisk> disks) {
        return new PipelineRunWithType(run, ToolAddress.empty(), null, null, disks, ComputeType.CPU);
    }
}
