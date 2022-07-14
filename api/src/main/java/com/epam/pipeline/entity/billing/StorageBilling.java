package com.epam.pipeline.entity.billing;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

@Value
@Builder(toBuilder = true)
public class StorageBilling implements PeriodBilling<StorageBillingMetrics> {

    Long id;
    String name;
    String owner;
    String billingCenter;
    DataStorageType type;
    String region;
    String provider;
    LocalDateTime created;
    StorageBillingMetrics totalMetrics;
    Map<Temporal, StorageBillingMetrics> periodMetrics;

    @Override
    public StorageBillingMetrics getPeriodMetrics(final Temporal period) {
        return Optional.ofNullable(periodMetrics.get(period)).orElseGet(StorageBillingMetrics::empty);
    }
}
