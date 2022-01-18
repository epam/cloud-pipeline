package com.epam.pipeline.manager.billing.index;

import java.time.LocalDate;

public interface BillingIndexHelper {

    String[] dailyIndicesBetween(LocalDate from, LocalDate to);
    String[] dailyRunIndicesBetween(LocalDate from, LocalDate to);
    String[] dailyStorageIndicesBetween(LocalDate from, LocalDate to);

    String[] monthlyIndicesBetween(LocalDate from, LocalDate to);
    String[] monthlyRunIndicesBetween(LocalDate from, LocalDate to);
    String[] monthlyStorageIndicesBetween(LocalDate from, LocalDate to);

    String[] yearlyIndicesBetween(LocalDate from, LocalDate to);
    String[] yearlyRunIndicesBetween(LocalDate from, LocalDate to);
    String[] yearlyStorageIndicesBetween(LocalDate from, LocalDate to);
}
