package com.epam.pipeline.entity.billing;

import lombok.Value;

@Value
public class BillingDiscount {

    int computes;
    int storages;

    public static BillingDiscount empty() {
        return new BillingDiscount(0, 0);
    }

    public boolean hasComputes() {
        return computes != 0;
    }

    public boolean hasStorages() {
        return storages != 0;
    }
}
