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
public class StorageReportBilling {
    Long id;
    String name;
    String owner;
    String billingCenter;
    DataStorageType type;
    String region;
    String provider;
    LocalDateTime created;
    Long cost;
    Long averageVolume;
    Long currentVolume;
    Map<YearMonth, StorageReportYearMonthBilling> billings;

    public Optional<StorageReportYearMonthBilling> getBilling(final YearMonth ym) {
        return Optional.ofNullable(billings.get(ym));
    }
}
