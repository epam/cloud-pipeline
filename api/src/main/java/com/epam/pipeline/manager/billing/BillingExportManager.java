package com.epam.pipeline.manager.billing;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.ResultWriter;
import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.utils.CommonUtils;
import com.epam.pipeline.utils.StreamUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BillingExportManager {

    private static final BillingExportType FALLBACK_BILLING_EXPORT_TYPE = BillingExportType.BLANK;
    private static final List<BillingExportType> FALLBACK_BILLING_EXPORT_TYPES = Collections.singletonList(
            FALLBACK_BILLING_EXPORT_TYPE);

    private final Map<BillingExportType, BillingExporter> exporters;
    private final MessageHelper messageHelper;

    public BillingExportManager(final List<BillingExporter> exporters,
                                final MessageHelper messageHelper) {
        this.exporters = CommonUtils.groupByKey(exporters, BillingExporter::getType);
        this.messageHelper = messageHelper;
    }

    public ResultWriter export(final BillingExportRequest request) {
        final List<BillingExportType> exportTypes = getBillingExportTypes(request);
        final List<BillingExporter> exporters = StreamUtils.interspersed(exportTypes.stream(), BillingExportType.BLANK)
                .map(this::getBillingExporter)
                .collect(Collectors.toList());
        Assert.isTrue(CollectionUtils.isNotEmpty(exporters),
                messageHelper.getMessage(MessageConstants.ERROR_BILLING_EXPORT_TYPES_MISSING));
        return ResultWriter.unchecked(getTitle(request, exporters), out -> export(request, out, exporters));
    }

    private String getTitle(final BillingExportRequest request, final List<BillingExporter> exporters) {
        return String.format("%s - %s - %s.csv", exporters.get(0).getName(), request.getFrom(), request.getTo());
    }

    private void export(final BillingExportRequest request,
                        final OutputStream out,
                        final List<BillingExporter> exporters) {
        try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out);
             BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter)) {
            exporters.forEach(exporter -> exporter.export(request, bufferedWriter));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        }
    }

    private List<BillingExportType> getBillingExportTypes(final BillingExportRequest request) {
        return Optional.ofNullable(request)
                .map(BillingExportRequest::getTypes)
                .orElse(FALLBACK_BILLING_EXPORT_TYPES);
    }

    private BillingExporter getBillingExporter(final BillingExportType exportType) {
        return Optional.ofNullable(exportType).map(Optional::of)
                .orElse(Optional.of(FALLBACK_BILLING_EXPORT_TYPE))
                .map(exporters::get)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_BILLING_EXPORT_TYPE_NOT_SUPPORTED, exportType)));
    }
}
