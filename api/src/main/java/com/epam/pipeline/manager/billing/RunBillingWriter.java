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

import com.epam.pipeline.entity.billing.RunBilling;
import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.Writer;

public class RunBillingWriter implements BillingWriter<RunBilling> {

    private final CSVWriter writer;

    public RunBillingWriter(final Writer writer) {
        this.writer = new CSVWriter(writer, BillingUtils.SEPARATOR);
    }

    @Override
    public void writeHeader() {
        writer.writeNext(new String[]{
            BillingUtils.RUN_COLUMN,
            BillingUtils.OWNER_COLUMN,
            BillingUtils.BILLING_CENTER_COLUMN,
            BillingUtils.PIPELINE_COLUMN,
            BillingUtils.TOOL_COLUMN,
            BillingUtils.TYPE_COLUMN,
            BillingUtils.INSTANCE_COLUMN,
            BillingUtils.STARTED_COLUMN,
            BillingUtils.FINISHED_COLUMN,
            BillingUtils.DURATION_COLUMN,
            BillingUtils.COST_COLUMN,
            BillingUtils.DISK_COST_COLUMN,
            BillingUtils.COMPUTE_COST_COLUMN
        });
    }

    @Override
    public void write(final RunBilling billing) {
        writer.writeNext(new String[]{
            BillingUtils.asString(billing.getRunId()),
            billing.getOwner(),
            billing.getBillingCenter(),
            billing.getPipeline(),
            billing.getTool(),
            billing.getComputeType(),
            billing.getInstanceType(),
            BillingUtils.asString(billing.getStarted()),
            BillingUtils.asString(billing.getFinished()),
            BillingUtils.asDurationString(billing.getDuration()),
            BillingUtils.asCostString(billing.getCost()),
            BillingUtils.asCostString(billing.getDiskCost()),
            BillingUtils.asCostString(billing.getComputeCost())
        });
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}
