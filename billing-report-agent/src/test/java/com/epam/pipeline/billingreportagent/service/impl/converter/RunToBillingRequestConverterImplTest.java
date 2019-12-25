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

package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.EntityContainer;
import com.epam.pipeline.billingreportagent.model.billing.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.mapper.RunBillingMapper;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("checkstyle:magicnumber")
public class RunToBillingRequestConverterImplTest {

    private static final Long RUN_ID = 1L;

    private final RunToBillingRequestConverter converter =
        new RunToBillingRequestConverter(new RunBillingMapper());

    @Test
    public void convertRunBillings() {
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

        final EntityContainer<PipelineRun> runContainer = EntityContainer.<PipelineRun>builder().entity(run).build();
        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 1, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(3, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(12, reports.get(LocalDate.of(2019, 12, 1)).getCost().longValue());
        Assert.assertEquals(48, reports.get(LocalDate.of(2019, 12, 2)).getCost().longValue());
        Assert.assertEquals(60, reports.get(LocalDate.of(2019, 12, 3)).getCost().longValue());
    }

    @Test
    public void convertRunBillingWithNoStatuses() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final EntityContainer<PipelineRun> runContainer = EntityContainer.<PipelineRun>builder().entity(run).build();

        final Collection<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(runContainer,
                                           LocalDateTime.of(2019, 12, 4, 0, 0),
                                           LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(1, billings.size());

        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(96, reports.get(LocalDate.of(2019, 12, 4)).getCost().longValue());
    }
}