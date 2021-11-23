package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.PipelineReportBilling;
import com.epam.pipeline.entity.billing.PipelineReportBillingMetrics;
import com.epam.pipeline.manager.preference.PreferenceManager;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class PipelineReportWriter implements Closeable {

    private static final int NUMERIC_SCALE = 2;
    private static final long DURATION_DIVISOR = TimeUnit.MINUTES.convert(NumberUtils.LONG_ONE, TimeUnit.HOURS);
    private static final long COST_DIVISOR = BigDecimal.ONE.setScale(4, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();

    private static final String TABLE_NAME = "Pipelines";
    private static final String PIPELINE_COLUMN = "Pipeline";
    private static final String OWNER_COLUMN = "Owner";
    private static final String RUNS_COUNT_COLUMN = "Runs (count)";
    private static final String RUNS_DURATION_COLUMN = "Duration (hours)";
    private static final String RUNS_COST_COLUMN = "Cost ($)";

    private final CommonReportWriter<PipelineReportBilling, PipelineReportBillingMetrics> writer;

    public PipelineReportWriter(final Writer writer,
                                final BillingHelper billingHelper,
                                final PreferenceManager preferenceManager,
                                final LocalDate from,
                                final LocalDate to) {
        this.writer = new CommonReportWriter<>(
                writer, billingHelper, preferenceManager, from, to,
                TABLE_NAME,
                Arrays.asList(PIPELINE_COLUMN, OWNER_COLUMN),
                Arrays.asList(RUNS_COUNT_COLUMN, RUNS_DURATION_COLUMN, RUNS_COST_COLUMN),
                Arrays.asList(
                        PipelineReportBilling::getName,
                        PipelineReportBilling::getOwner),
                Arrays.asList(
                        metrics -> billingHelper.asString(metrics.getRunsNumber()),
                        metrics -> divided(metrics.getRunsDuration(), DURATION_DIVISOR).toString(),
                        metrics -> divided(metrics.getRunsCost(), COST_DIVISOR).toString()));
    }

    public void writeHeader() {
        writer.writeHeader();
    }

    public void write(final PipelineReportBilling billing) {
        writer.write(billing);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private BigDecimal divided(final Long divider, final Long divisor) {
        return Optional.ofNullable(divider)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(divisor), NUMERIC_SCALE, RoundingMode.CEILING);
    }
}
