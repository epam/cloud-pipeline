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
import com.epam.pipeline.elasticsearch.ElasticStackVersion;
import com.epam.pipeline.elasticsearch.model.DocWriteRequest;
import com.epam.pipeline.elasticsearch.model.IndexRequest;
import com.epam.pipeline.entity.cluster.NodeDisk;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunPrice;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatusInfo;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.utils.DateUtils;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        new RunToBillingRequestConverter(new RunBillingMapper(BILLING_CENTER_KEY), ElasticStackVersion.V6);

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
        final Map<String, ?> requestFieldsMap = ((IndexRequest) billing).sourceAsMap();
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

    // ========== GRANULAR PRICING TESTS ==========

    @Test
    public void testGranularPricingShouldUsePriceSnapshotFromStatus() {
        // Scenario: run with price snapshot in status should use snapshot price, not run-level price
        final BigDecimal runLevelComputePrice = new BigDecimal("2.43200");
        final BigDecimal runLevelDiskPrice = new BigDecimal("0.00013");
        final BigDecimal snapshotComputePrice = new BigDecimal("0.09700");
        final BigDecimal snapshotDiskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 11, 40, 38),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        snapshotComputePrice, snapshotDiskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 12, 30, 0)));
        run.setPricePerHour(runLevelComputePrice.add(runLevelDiskPrice));
        run.setComputePricePerHour(runLevelComputePrice);
        run.setDiskPricePerHour(runLevelDiskPrice);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        // 30 minutes = 1800 seconds
        // Formula: duration * price / 3600, scale to 2 decimals with CEILING, * 10000
        // compute cost = (1800 * 0.097) / 3600 = 0.0485, rounded to 0.05 (CEILING), * 10000 = 500
        // 500 hundredths of cents = 5 cents = $0.05
        // Expected: 500 (using snapshot price 0.097, not run-level 2.432)
        assertEquals(500L, billing.getComputeCost().longValue());
    }

    @Test
    public void testGranularPricingShouldFallbackToRunLevelPriceWhenNoSnapshot() {
        // Scenario: status without price snapshot should use run-level price
        final BigDecimal runLevelComputePrice = new BigDecimal("2.43200");
        final BigDecimal runLevelDiskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 11, 40, 38),
                status(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 12, 30, 0)));
        run.setPricePerHour(new BigDecimal("2.44"));
        run.setComputePricePerHour(runLevelComputePrice);
        run.setDiskPricePerHour(runLevelDiskPrice);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        // 30 minutes = 1800 seconds
        // Formula: (duration * price / 3600), scale to 2 decimals with CEILING, * 10000
        // compute cost = (1800 * 2.432) / 3600 = 1.216, rounded to 1.22 (CEILING), * 10000 = 12200
        // 12200 hundredths of cents = 122 cents = $1.22
        assertEquals(12200L, billing.getComputeCost().longValue());
    }

    @Test
    public void testGranularPricingDiskCostCalculation() {
        // Scenario: a run is paused for 30 minutes and resumed on another instance type,
        // so both its compute and its disk price change. Every segment has to be billed
        // by the price snapshot of the status it starts with, and never by the run level price.
        final BigDecimal beforeResumeComputePrice = new BigDecimal("0.09600");
        final BigDecimal beforeResumeDiskPrice = new BigDecimal("0.00100"); // $0.001 per GB per hour
        final BigDecimal afterResumeComputePrice = new BigDecimal("0.38400");
        final BigDecimal afterResumeDiskPrice = new BigDecimal("0.00200"); // $0.002 per GB per hour

        final List<NodeDisk> disks = Arrays.asList(
                disk(100L, LocalDateTime.of(2026, 9, 22, 12, 0, 0))); // 100 GB disk

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                // 1 hour of running on the original instance type
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        beforeResumeComputePrice, beforeResumeDiskPrice),
                // 30 minutes of being paused, the price is not changed yet
                statusWithPrice(TaskStatus.PAUSED, LocalDateTime.of(2026, 9, 22, 13, 0, 0),
                        beforeResumeComputePrice, beforeResumeDiskPrice),
                // 1 hour of running on the instance type the run was resumed on
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 13, 30, 0),
                        afterResumeComputePrice, afterResumeDiskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 14, 30, 0)));
        // the run level price differs from both snapshots, so any fallback to it is visible
        run.setPricePerHour(new BigDecimal("2.44"));
        run.setComputePricePerHour(new BigDecimal("2.43200"));
        run.setDiskPricePerHour(new BigDecimal("0.00500"));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, disks))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        assertEquals(120L, billing.getUsageMinutes().longValue()); // 60 + 60 active minutes
        assertEquals(30L, billing.getPausedMinutes().longValue());

        // Every segment is priced separately and rounded to cents with CEILING.
        // Compute, the paused segment is inactive and adds nothing:
        //   1 hour * 0.096 = 0.096 -> 0.10 -> 1000
        //   1 hour * 0.384 = 0.384 -> 0.39 -> 3900
        assertEquals(4900L, billing.getComputeCost().longValue());
        // Disk, 100 GB, charged for the paused segment as well:
        //   1 hour * 100 GB * 0.001 = 0.10 -> 1000
        //   30 min * 100 GB * 0.001 = 0.05 -> 500
        //   1 hour * 100 GB * 0.002 = 0.20 -> 2000
        assertEquals(3500L, billing.getDiskCost().longValue());
        assertEquals(8400L, billing.getCost().longValue());
    }

    @Test
    public void testGranularPricingBillsPausingAndResumingStatusesAsInactive() {
        // Full pause/resume cycle: only RUNNING segments are billed as compute,
        // PAUSING/PAUSED/RESUMING segments are billed as paused regardless of their price snapshot
        final BigDecimal beforePauseComputePrice = new BigDecimal("2.43200");
        final BigDecimal afterResumeComputePrice = new BigDecimal("0.09700");
        final BigDecimal diskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        beforePauseComputePrice, diskPrice),
                statusWithPrice(TaskStatus.PAUSING, LocalDateTime.of(2026, 9, 22, 13, 0, 0),
                        beforePauseComputePrice, diskPrice),
                statusWithPrice(TaskStatus.PAUSED, LocalDateTime.of(2026, 9, 22, 13, 15, 0),
                        beforePauseComputePrice, diskPrice),
                statusWithPrice(TaskStatus.RESUMING, LocalDateTime.of(2026, 9, 22, 13, 45, 0),
                        afterResumeComputePrice, diskPrice),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 14, 0, 0),
                        afterResumeComputePrice, diskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 16, 0, 0)));
        // Run level prices differ from both snapshots, so they cannot produce the expected values
        run.setPricePerHour(new BigDecimal("9.99"));
        run.setComputePricePerHour(new BigDecimal("9.99000"));
        run.setDiskPricePerHour(new BigDecimal("0.00500"));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        // Active segments (RUNNING is the status that begins them):
        //   12:00-13:00 (1 h)  * 2.43200 = 2.43200 -> CEILING 2.44 -> 24400
        //   14:00-16:00 (2 h)  * 0.09700 = 0.19400 -> CEILING 0.20 -> 2000
        // Inactive segments (PAUSING, PAUSED and RESUMING begin them): no compute cost at all,
        //   even though PAUSING and PAUSED carry the expensive 2.43200 snapshot
        assertEquals(180L, billing.getUsageMinutes().longValue());
        assertEquals(60L, billing.getPausedMinutes().longValue());
        assertEquals(26400L, billing.getComputeCost().longValue());
        // No disks are attached, so the total cost is the compute cost only
        assertEquals(0L, billing.getDiskCost().longValue());
        assertEquals(26400L, billing.getCost().longValue());
    }

    @Test
    public void testGranularPricingPauseAndResumeOnAnotherInstanceType() {
        // Scenario from the issue: 3 hours on a cheap instance type, then 3 hours on an expensive one
        final BigDecimal initialComputePrice = new BigDecimal("0.09600");
        final BigDecimal resumedComputePrice = new BigDecimal("0.38400");
        final BigDecimal diskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 6, 0, 0),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 6, 0, 0),
                        initialComputePrice, diskPrice),
                statusWithPrice(TaskStatus.PAUSED, LocalDateTime.of(2026, 9, 22, 9, 0, 0),
                        initialComputePrice, diskPrice),
                // resumed on a bigger instance type, the price snapshot is already updated
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 10, 0, 0),
                        resumedComputePrice, diskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 13, 0, 0)));
        // the run level price holds the latest instance type price only
        run.setPricePerHour(new BigDecimal("0.39"));
        run.setComputePricePerHour(resumedComputePrice);
        run.setDiskPricePerHour(diskPrice);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        // 3 hours * 0.096 = 0.288 -> 0.29 (CEILING) -> 2900
        // 3 hours * 0.384 = 1.152 -> 1.16 (CEILING) -> 11600
        assertEquals(14500L, billing.getComputeCost().longValue());
        assertEquals(360L, billing.getUsageMinutes().longValue());
        assertEquals(60L, billing.getPausedMinutes().longValue());
    }

    @Test
    public void testGranularPricingSyntheticFirstRunningStatusInheritsFollowingSnapshot() {
        final BigDecimal beforePausePrice = new BigDecimal("2.43200");
        final BigDecimal afterResumePrice = new BigDecimal("0.09700");
        final BigDecimal diskPrice = new BigDecimal("0.00013");

        // the run was active since 12:00, but the first recorded status is the pause at 13:00
        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                statusWithPrice(TaskStatus.PAUSED, LocalDateTime.of(2026, 9, 22, 13, 0, 0),
                        beforePausePrice, diskPrice),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 14, 0, 0),
                        afterResumePrice, diskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 15, 0, 0)));
        // run level price differs from both snapshots to make sure it is not used
        run.setPricePerHour(new BigDecimal("9.99"));
        run.setComputePricePerHour(new BigDecimal("9.99000"));
        run.setDiskPricePerHour(diskPrice);

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2026, 9, 22).atStartOfDay(),
                LocalDate.of(2026, 9, 23).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0)),
                status(TaskStatus.PAUSED, LocalDateTime.of(2026, 9, 22, 13, 0, 0)),
                status(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 14, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 15, 0, 0)));
        // the synthetic running status is priced as the pause it precedes
        assertEquals(beforePausePrice,
                adjustedStatuses.get(0).getRunStatusInfo().getPrice().getComputePricePerHour());

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();

        // 1 hour * 2.432 = 2.432 -> 2.44 (CEILING) -> 24400 for the synthetic running period
        // 1 hour paused -> no compute costs
        // 1 hour * 0.097 = 0.097 -> 0.10 (CEILING) -> 1000
        assertEquals(25400L, billing.getComputeCost().longValue());
        assertEquals(120L, billing.getUsageMinutes().longValue());
        assertEquals(60L, billing.getPausedMinutes().longValue());
    }

    @Test
    public void testGranularPricingAdjustedStatusesKeepSnapshots() {
        final BigDecimal computePrice = new BigDecimal("0.09700");
        final BigDecimal diskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 21, 12, 0, 0),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 21, 12, 0, 0),
                        computePrice, diskPrice));

        final List<RunStatus> adjustedStatuses = converter.adjustStatuses(run,
                LocalDate.of(2026, 9, 22).atStartOfDay(),
                LocalDate.of(2026, 9, 23).atStartOfDay());

        assertRunsActivityStats(adjustedStatuses,
                status(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 0, 0, 0)),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 23, 0, 0, 0)));
        // the running status shifted to the period start keeps its snapshot
        assertEquals(computePrice,
                adjustedStatuses.get(0).getRunStatusInfo().getPrice().getComputePricePerHour());
        // the synthetic stopped status closes the period and requires no snapshot
        assertNull(adjustedStatuses.get(1).getRunStatusInfo());
    }

    @Test
    public void testGranularPricingShouldFallbackToRunLevelPriceWhenSnapshotComputePriceIsMissing() {
        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        RunStatusInfo.builder().price(RunPrice.builder()
                                .pricePerHour(new BigDecimal("0.10"))
                                .build()).build()),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 13, 0, 0)));
        run.setPricePerHour(new BigDecimal("2.44"));
        run.setComputePricePerHour(new BigDecimal("2.43200"));
        run.setDiskPricePerHour(new BigDecimal("0.00013"));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        // 1 hour * run level 2.432 -> 2.44 (CEILING) -> 24400
        assertEquals(24400L, billings.iterator().next().getComputeCost().longValue());
    }

    @Test
    public void testGranularPricingShouldUseSnapshotPricesWhenSnapshotPricePerHourIsMissing() {
        // the price which is missing in the snapshot is taken from the run level price,
        // the ones which are present are still taken from the snapshot
        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        RunStatusInfo.builder().price(RunPrice.builder()
                                .computePricePerHour(new BigDecimal("0.09700"))
                                .diskPricePerHour(new BigDecimal("0.00013"))
                                .build()).build()),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 13, 0, 0)));
        run.setPricePerHour(new BigDecimal("2.44"));
        run.setComputePricePerHour(new BigDecimal("2.43200"));
        run.setDiskPricePerHour(new BigDecimal("0.00013"));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        // 1 hour * snapshot 0.097 -> 0.10 (CEILING) -> 1000
        assertEquals(1000L, billings.iterator().next().getComputeCost().longValue());
    }

    @Test
    public void testGranularPricingShouldFallbackToRunLevelPriceWhenSnapshotDiskPriceIsMissing() {
        final List<NodeDisk> disks = Arrays.asList(
                disk(100L, LocalDateTime.of(2026, 9, 22, 12, 0, 0)));

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 12, 0, 0),
                        RunStatusInfo.builder().price(RunPrice.builder()
                                .pricePerHour(new BigDecimal("0.10"))
                                .computePricePerHour(new BigDecimal("0.09700"))
                                .build()).build()),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 22, 13, 0, 0)));
        run.setPricePerHour(new BigDecimal("2.44"));
        run.setComputePricePerHour(new BigDecimal("2.43200"));
        run.setDiskPricePerHour(new BigDecimal("0.00100"));

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, disks))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 23).atStartOfDay());

        assertEquals(1, billings.size());
        final PipelineRunBillingInfo billing = billings.iterator().next();
        // 1 hour * snapshot 0.097 -> 0.10 (CEILING) -> 1000
        assertEquals(1000L, billing.getComputeCost().longValue());
        // 1 hour * 100 GB * run level 0.001 -> 0.10 (CEILING) -> 1000
        assertEquals(1000L, billing.getDiskCost().longValue());
    }

    @Test
    public void testGranularPricingShouldSplitSegmentPricesBetweenDays() {
        final BigDecimal beforeResumePrice = new BigDecimal("0.09600");
        final BigDecimal afterResumePrice = new BigDecimal("0.38400");
        final BigDecimal diskPrice = new BigDecimal("0.00013");

        final PipelineRun run = run(
                LocalDateTime.of(2026, 9, 22, 22, 0, 0),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 22, 22, 0, 0),
                        beforeResumePrice, diskPrice),
                statusWithPrice(TaskStatus.RUNNING, LocalDateTime.of(2026, 9, 23, 2, 0, 0),
                        afterResumePrice, diskPrice),
                status(TaskStatus.STOPPED, LocalDateTime.of(2026, 9, 23, 4, 0, 0)));
        run.setPricePerHour(new BigDecimal("0.39"));
        run.setComputePricePerHour(afterResumePrice);
        run.setDiskPricePerHour(diskPrice);

        final EntityContainer<PipelineRunWithType> runContainer = EntityContainer.<PipelineRunWithType>builder()
                .entity(runEntity(run, Collections.emptyList()))
                .build();

        final Collection<PipelineRunBillingInfo> billings =
                converter.convertRunToBillings(runContainer,
                        LocalDate.of(2026, 9, 22).atStartOfDay(),
                        LocalDate.of(2026, 9, 24).atStartOfDay());

        final Map<LocalDate, PipelineRunBillingInfo> reports = billings.stream()
                .collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        assertEquals(2, reports.size());
        // 22.09: 2 hours * 0.096 = 0.192 -> 0.20 (CEILING) -> 2000
        assertEquals(2000L, reports.get(LocalDate.of(2026, 9, 22)).getComputeCost().longValue());
        // 23.09: 2 hours * 0.096 = 0.192 -> 0.20 -> 2000 and 2 hours * 0.384 = 0.768 -> 0.77 -> 7700
        assertEquals(9700L, reports.get(LocalDate.of(2026, 9, 23)).getComputeCost().longValue());
    }

    private RunStatus statusWithPrice(final TaskStatus status, final LocalDateTime date,
                                      final BigDecimal computePricePerHour,
                                      final BigDecimal diskPricePerHour) {
        final RunStatusInfo priceInfo = RunStatusInfo.of(computePricePerHour.add(diskPricePerHour),
                computePricePerHour, diskPricePerHour);
        return new RunStatus(RUN_ID, status, date, priceInfo);
    }
}
