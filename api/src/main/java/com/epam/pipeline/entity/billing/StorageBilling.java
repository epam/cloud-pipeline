package com.epam.pipeline.entity.billing;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class StorageBilling implements ReportBilling<StorageBillingMetrics> {
    Long id;
    String name;
    String owner;
    String billingCenter;
    DataStorageType type;
    String region;
    String provider;
    LocalDateTime created;
    StorageBillingMetrics totalMetrics;
    Map<YearMonth, StorageBillingMetrics> periodMetrics;

    @Override
    public StorageBillingMetrics getPeriodMetrics(final YearMonth ym) {
        return Optional.ofNullable(periodMetrics.get(ym)).orElseGet(StorageBillingMetrics::empty);
    }
}
