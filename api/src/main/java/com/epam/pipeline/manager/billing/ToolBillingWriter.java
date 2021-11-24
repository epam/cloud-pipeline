package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.ToolBilling;
import com.epam.pipeline.entity.billing.ToolBillingMetrics;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.Arrays;

@RequiredArgsConstructor
public class ToolBillingWriter implements BillingWriter<ToolBilling> {

    private static final String TABLE_NAME = "Tools";
    private static final String TOOL_COLUMN = "Tool";
    private static final String OWNER_COLUMN = "Owner";
    private static final String RUNS_COUNT_COLUMN = "Runs (count)";
    private static final String RUNS_DURATION_COLUMN = "Duration (hours)";
    private static final String RUNS_COST_COLUMN = "Cost ($)";

    private final PeriodBillingWriter<ToolBilling, ToolBillingMetrics> writer;

    public ToolBillingWriter(final Writer writer,
                             final LocalDate from,
                             final LocalDate to) {
        this.writer = new PeriodBillingWriter<>(writer, from, to, TABLE_NAME,
                Arrays.asList(TOOL_COLUMN, OWNER_COLUMN),
                Arrays.asList(RUNS_COUNT_COLUMN, RUNS_DURATION_COLUMN, RUNS_COST_COLUMN),
                Arrays.asList(
                        billing -> ToolUtils.getImageWithoutRepository(billing.getName()).orElse(StringUtils.EMPTY),
                        ToolBilling::getOwner),
                Arrays.asList(
                        metrics -> BillingUtils.asString(metrics.getRunsNumber()),
                        metrics -> BillingUtils.asDurationString(metrics.getRunsDuration()),
                        metrics -> BillingUtils.asCostString(metrics.getRunsCost())));
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
