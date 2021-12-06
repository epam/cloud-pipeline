package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;

import java.io.OutputStream;

public interface BillingExporter {
    BillingExportType getType();
    void export(BillingExportRequest request, OutputStream out);
}
