/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.InstanceBilling;
import com.epam.pipeline.entity.billing.InstanceBillingMetrics;
import com.epam.pipeline.utils.CommonUtils;

import java.io.Writer;
import java.util.Collections;
import java.util.stream.Stream;

public class InstanceBillingWriterTest extends AbstractBillingWriterTest<InstanceBilling> {

    public InstanceBillingWriterTest() {
        super("/billings/billing.instance.csv");
    }

    @Override
    public BillingWriter<InstanceBilling> getWriter(final Writer writer) {
        return new InstanceBillingWriter(writer, START_DATE, FINISH_DATE);
    }

    @Override
    public Stream<InstanceBilling> billings() {
        return Stream.of(
                InstanceBilling.builder()
                        .name(INSTANCE_TYPE)
                        .periodMetrics(CommonUtils.mergeMaps(
                                Collections.singletonMap(START_YEAR_MONTH,
                                        InstanceBillingMetrics.builder()
                                                .runsNumber(ONE)
                                                .runsDuration(ONE_HOUR)
                                                .runsCost(ONE_DOLLAR)
                                                .runsDiskCost(ONE_DOLLAR)
                                                .runsComputeCost(ONE_DOLLAR)
                                                .build()),
                                Collections.singletonMap(FINISH_YEAR_MONTH,
                                        InstanceBillingMetrics.builder()
                                                .runsNumber(TEN)
                                                .runsDuration(TEN_HOURS)
                                                .runsCost(TEN_DOLLARS)
                                                .runsDiskCost(TEN_DOLLARS)
                                                .runsComputeCost(TEN_DOLLARS)
                                                .build())))
                        .totalMetrics(InstanceBillingMetrics.builder()
                                .runsNumber(ONE + TEN)
                                .runsDuration(ONE_HOUR + TEN_HOURS)
                                .runsCost(ONE_DOLLAR + TEN_DOLLARS)
                                .runsDiskCost(ONE_DOLLAR + TEN_DOLLARS)
                                .runsComputeCost(ONE_DOLLAR + TEN_DOLLARS)
                                .build())
                        .build());
    }
}
