package com.epam.pipeline.manager.billing;

import com.epam.pipeline.config.Constants;
import org.apache.commons.lang3.math.NumberUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class BillingUtils {

    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.FMT_ISO_LOCAL_DATE);
    public static final int NUMERIC_SCALE = 2;
    public static final long DURATION_DIVISOR = TimeUnit.MINUTES.convert(NumberUtils.LONG_ONE, TimeUnit.HOURS);
    public static final long COST_DIVISOR = BigDecimal.ONE.setScale(4, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();
    private static final long VOLUME_DIVISOR = BigDecimal.ONE.setScale(9, RoundingMode.CEILING)
            .unscaledValue()
            .longValue();

    public static String asString(final Object value) {
        return Optional.ofNullable(value).map(Objects::toString).orElse(null);
    }

    public static String asString(final LocalDateTime value) {
        return Optional.ofNullable(value).map(DATE_TIME_FORMATTER::format).orElse(null);
    }

    public static LocalDateTime asDateTime(final Object value) {
        return asDateTime(asString(value));
    }

    public static LocalDateTime asDateTime(final String value) {
        return Optional.ofNullable(value)
                .map(it -> DATE_TIME_FORMATTER.parse(it, LocalDateTime::from))
                .orElse(null);
    }

    public static BigDecimal divided(final Long divider, final Long divisor) {
        return Optional.ofNullable(divider)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(divisor), NUMERIC_SCALE, RoundingMode.CEILING);
    }

    public static String asDurationString(final Long value) {
        return divided(value, DURATION_DIVISOR).toString();
    }

    public static String asCostString(final Long value) {
        return divided(value, COST_DIVISOR).toString();
    }

    public static String asVolumeString(final Long value) {
        return divided(value, VOLUME_DIVISOR).toString();
    }

}
