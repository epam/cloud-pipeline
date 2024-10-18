package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.exception.search.SearchException;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.io.Writer;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
public class CommonBillingExporter<B> implements BillingExporter {

    @Getter
    private final String name;
    @Getter
    private final BillingExportType type;
    private final BillingLoader<B> loader;
    private final BiFunction<BillingExportRequest, Writer, BillingWriter<B>> writerSupplier;
    private final GlobalSearchElasticHelper elasticHelper;

    @Override
    public void export(final BillingExportRequest request, final Writer writer) {
        final BillingWriter<B> billingWriter = writerSupplier.apply(request, writer);
        try (RestHighLevelClient elasticSearchClient = elasticHelper.buildBillingClient()) {
            billingWriter.writeHeader();
            loader.billings(elasticSearchClient, request).forEach(billingWriter::write);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new SearchException(e.getMessage(), e);
        } finally {
            try {
                billingWriter.flush();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}
