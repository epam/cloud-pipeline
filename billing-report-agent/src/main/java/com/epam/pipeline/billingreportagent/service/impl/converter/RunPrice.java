package com.epam.pipeline.billingreportagent.service.impl.converter;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class RunPrice {
    BigDecimal oldFashionedPricePerHour;
    BigDecimal computePricePerHour;
    BigDecimal diskPricePerHour;

    public boolean isOldFashioned() {
        return computePricePerHour.compareTo(BigDecimal.ZERO) == 0 && diskPricePerHour.compareTo(BigDecimal.ZERO) == 0;
    }
    
    public boolean isNewFashioned() {
        return !isOldFashioned();
    }
}
