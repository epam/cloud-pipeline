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

import com.epam.pipeline.billingreportagent.model.PipelineRunBillingInfo;
import com.epam.pipeline.billingreportagent.service.impl.CloudPipelineAPIClient;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.BillingMapper;
import com.epam.pipeline.billingreportagent.service.impl.converter.run.PipelineRunLoader;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class RunToBillingRequestConverterImplTest {

    private static final String TEST_INDEX = "test-index-1";
    private static final Long RUN_ID = 1L;

    @Mock
    CloudPipelineAPIClient apiClient;

    private final RunToBillingRequestConverterImpl converter =
        new RunToBillingRequestConverterImpl(TEST_INDEX, new PipelineRunLoader(apiClient), new BillingMapper());

    @Test
    @SuppressWarnings("checkstyle:magicnumber")
    public void convertRunBillings() {
        final PipelineRun run = new PipelineRun();
        run.setId(RUN_ID);
        run.setPricePerHour(BigDecimal.valueOf(4, 2));
        final List<RunStatus> statuses = new ArrayList<>();
        statuses.add(new RunStatus(RUN_ID, TaskStatus.RUNNING, LocalDateTime.of(2019, 12, 1, 12, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.PAUSED, LocalDateTime.of(2019, 12, 2, 15, 0)));
        statuses.add(new RunStatus(RUN_ID, TaskStatus.STOPPED, LocalDateTime.of(2019, 12, 3, 15, 0)));

        run.setRunStatuses(statuses);
        final List<PipelineRunBillingInfo> billings =
            converter.convertRunToBillings(run, LocalDateTime.of(2019, 12, 5, 0, 0));
        Assert.assertEquals(2, billings.size());
        final Map<LocalDate, PipelineRunBillingInfo> reports =
            billings.stream().collect(Collectors.toMap(PipelineRunBillingInfo::getDate, Function.identity()));
        Assert.assertEquals(48, reports.get(LocalDate.of(2019, 12, 1)).getCost().longValue());
        Assert.assertEquals(60, reports.get(LocalDate.of(2019, 12, 2)).getCost().longValue());
    }
}