package com.epam.pipeline.app;

import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.manager.billing.BillingCenterBillingLoader;
import com.epam.pipeline.manager.billing.BillingCenterBillingWriter;
import com.epam.pipeline.manager.billing.BillingExporter;
import com.epam.pipeline.manager.billing.InstanceBillingLoader;
import com.epam.pipeline.manager.billing.InstanceBillingWriter;
import com.epam.pipeline.manager.billing.PipelineBillingLoader;
import com.epam.pipeline.manager.billing.PipelineBillingWriter;
import com.epam.pipeline.manager.billing.RunBillingLoader;
import com.epam.pipeline.manager.billing.RunBillingWriter;
import com.epam.pipeline.manager.billing.StorageBillingLoader;
import com.epam.pipeline.manager.billing.StorageBillingWriter;
import com.epam.pipeline.manager.billing.CommonBillingExporter;
import com.epam.pipeline.manager.billing.ToolBillingLoader;
import com.epam.pipeline.manager.billing.ToolBillingWriter;
import com.epam.pipeline.manager.billing.UserBillingLoader;
import com.epam.pipeline.manager.billing.UserBillingWriter;
import com.epam.pipeline.manager.billing.index.BillingIndexHelper;
import com.epam.pipeline.manager.billing.index.BoundingBillingIndexHelper;
import com.epam.pipeline.manager.billing.index.DailyBillingIndexHelper;
import com.epam.pipeline.manager.billing.index.PeriodBillingIndexHelper;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingConfiguration {

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    @Value("${billing.index.common.prefix:cp-billing-}")
    private String commonIndexPrefix;

    @Value("${billing.run.index.name:pipeline-run}")
    private String runIndexName;

    @Value("${billing.storage.index.name:storage}")
    private String storageIndexName;

    @Bean
    @ConditionalOnProperty(value = "billing.index.period.disable", matchIfMissing = true, havingValue = FALSE)
    public BillingIndexHelper periodBillingIndexHelper(final GlobalSearchElasticHelper elasticHelper) {
        final BillingIndexHelper helper = new PeriodBillingIndexHelper(commonIndexPrefix, runIndexName,
                storageIndexName);
        return new BoundingBillingIndexHelper(helper, elasticHelper, commonIndexPrefix, runIndexName, storageIndexName);
    }

    @Bean
    @ConditionalOnProperty(value = "billing.index.period.disable", havingValue = TRUE)
    public BillingIndexHelper dailyBillingIndexHelper() {
        return new DailyBillingIndexHelper(commonIndexPrefix, runIndexName, storageIndexName);
    }

    @Bean
    public BillingExporter runBillingExporter(final RunBillingLoader runBillingLoader,
                                              final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("Runs Report",
            BillingExportType.RUN,
            runBillingLoader,
            (request, writer) -> new RunBillingWriter(writer),
            elasticHelper);
    }

    @Bean
    public BillingExporter userBillingExporter(final UserBillingLoader userBillingLoader,
                                               final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("General Users Report",
            BillingExportType.USER,
            userBillingLoader,
            (request, writer) -> new UserBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }

    @Bean
    public BillingExporter billingCenterBillingExporter(final BillingCenterBillingLoader billingCenterBillingLoader,
                                                        final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("General Billing Centers Report",
            BillingExportType.BILLING_CENTER,
            billingCenterBillingLoader,
            (request, writer) -> new BillingCenterBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }

    @Bean
    public BillingExporter storageBillingExporter(final StorageBillingLoader storageBillingLoader,
                                                  final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("Storages Report",
            BillingExportType.STORAGE,
            storageBillingLoader,
            (request, writer) -> new StorageBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }

    @Bean
    public BillingExporter instanceBillingExporter(final InstanceBillingLoader instanceBillingLoader,
                                                   final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("Instances Report",
            BillingExportType.INSTANCE,
            instanceBillingLoader,
            (request, writer) -> new InstanceBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }

    @Bean
    public BillingExporter pipelineBillingExporter(final PipelineBillingLoader pipelineBillingLoader,
                                                   final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("Pipelines Report",
            BillingExportType.PIPELINE,
            pipelineBillingLoader,
            (request, writer) -> new PipelineBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }

    @Bean
    public BillingExporter toolBillingExporter(final ToolBillingLoader toolBillingLoader,
                                               final GlobalSearchElasticHelper elasticHelper) {
        return new CommonBillingExporter<>("Tools Report",
            BillingExportType.TOOL,
            toolBillingLoader,
            (request, writer) -> new ToolBillingWriter(writer, request.getFrom(), request.getTo()),
            elasticHelper);
    }
}
