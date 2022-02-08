package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlankBillingExporter implements BillingExporter {

    private static final char SEPARATOR = ',';

    @Override
    public String getName() {
        return "Blank Report";
    }

    @Override
    public BillingExportType getType() {
        return BillingExportType.BLANK;
    }

    @Override
    public void export(final BillingExportRequest request, final Writer writer) {
        final CSVWriter billingWriter = new CSVWriter(writer, SEPARATOR);
        try {
            billingWriter.writeNext(new String[]{});
        } finally {
            try {
                billingWriter.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
