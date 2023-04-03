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

import com.epam.pipeline.entity.billing.ToolBilling;
import com.epam.pipeline.entity.billing.ToolBillingMetrics;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

public class ToolBillingWriter implements BillingWriter<ToolBilling> {

    private static final String TABLE_NAME = "Tools";

    private final PeriodBillingWriter<ToolBilling, ToolBillingMetrics> writer;

    public ToolBillingWriter(final Writer writer,
                             final LocalDate from,
                             final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(
                    BillingUtils.TOOL_COLUMN,
                    BillingUtils.OWNER_COLUMN),
                Arrays.asList(
                    BillingUtils.RUNS_COUNT_COLUMN,
                    BillingUtils.DURATION_COLUMN,
                    BillingUtils.COST_COLUMN,
                    BillingUtils.DISK_COST_COLUMN,
                    BillingUtils.COMPUTE_COST_COLUMN),
                Arrays.asList(
                    billing -> ToolUtils.getImageWithoutRepository(billing.getName()).orElse(StringUtils.EMPTY),
                    ToolBilling::getOwner),
                Arrays.asList(
                    metrics -> BillingUtils.asString(metrics.getRunsNumber()),
                    metrics -> BillingUtils.asDurationString(metrics.getRunsDuration()),
                    metrics -> BillingUtils.asCostString(metrics.getRunsCost()),
                    metrics -> BillingUtils.asCostString(metrics.getRunsDiskCost()),
                    metrics -> BillingUtils.asCostString(metrics.getRunsComputeCost())));
    }

    @Override
    public void writeHeader() {
        writer.writeHeader();
    }

    @Override
    public void write(final ToolBilling billing) {
        writer.write(billing);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}
