package com.epam.pipeline.manager.billing;

import com.epam.pipeline.controller.vo.billing.BillingExportRequest;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.stream.Stream;

public interface BillingLoader<B> {

    Stream<B> billings(RestHighLevelClient elasticSearchClient,
                       BillingExportRequest request);
}
