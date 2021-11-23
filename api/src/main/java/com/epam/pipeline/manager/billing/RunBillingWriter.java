package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.billing.RunBilling;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class RunBillingWriter implements BillingWriter {

    private static final char SEPARATOR = ',';
    private static final int NUMERIC_SCALE = 2;
    private static final long DURATION_DIVISOR = TimeUnit.MINUTES.convert(NumberUtils.LONG_ONE, TimeUnit.HOURS);
    private static final long COST_DIVISOR = BigDecimal.ONE.setScale(4, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    private static final String[] FALLBACK_HEADER = new String[]{
        "Run", "Owner", "Billing Center", "Pipeline", "Tool", "Type", "Instance", "Started", "Finished",
        "Duration (hours)", "Cost ($)"};

    private final CSVWriter writer;
    private final BillingHelper billingHelper;
    private final PreferenceManager preferenceManager;

    public RunBillingWriter(final Writer writer,
                            final BillingHelper billingHelper,
                            final PreferenceManager preferenceManager) {
        this.writer = new CSVWriter(writer, SEPARATOR);
        this.billingHelper = billingHelper;
        this.preferenceManager = preferenceManager;
    }

    public void writeHeader() {
        writer.writeNext(getHeader());
    }

    private String[] getHeader() {
        return Optional.of(SystemPreferences.BILLING_EXPORT_RUN_HEADER)
                .map(preferenceManager::getPreference)
                .filter(StringUtils::isNotBlank)
                .map(it -> StringUtils.split(it, SEPARATOR))
                .orElse(FALLBACK_HEADER);
    }

    public void write(final RunBilling billing) {
        writer.writeNext(new String[]{
                billingHelper.asString(billing.getRunId()),
                billing.getOwner(),
                billing.getBillingCenter(),
                billing.getPipeline(),
                billing.getTool(),
                billing.getComputeType(),
                billing.getInstanceType(),
                billingHelper.asString(billing.getStarted()),
                billingHelper.asString(billing.getFinished()),
                divided(billing.getDuration(), DURATION_DIVISOR).toString(),
                divided(billing.getCost(), COST_DIVISOR).toString()});
    }

    private BigDecimal divided(final Long divider, final Long divisor) {
        return Optional.ofNullable(divider)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(divisor), NUMERIC_SCALE, RoundingMode.CEILING);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }
}
