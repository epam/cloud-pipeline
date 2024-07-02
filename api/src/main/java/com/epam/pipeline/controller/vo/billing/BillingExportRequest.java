package com.epam.pipeline.controller.vo.billing;

import com.epam.pipeline.entity.billing.BillingDiscount;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
public class BillingExportRequest {
    LocalDate from;
    LocalDate to;
    Map<String, List<String>> filters;
    List<BillingExportType> types;
    BillingDiscount discount;
    BillingExportProperties properties;
}
