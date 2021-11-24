package com.epam.pipeline.app;

import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.entity.billing.UserGeneralBilling;
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
import com.epam.pipeline.manager.billing.StreamingBillingExporter;
import com.epam.pipeline.manager.billing.ToolBillingLoader;
import com.epam.pipeline.manager.billing.ToolBillingWriter;
import com.epam.pipeline.manager.billing.UserBillingLoader;
import com.epam.pipeline.manager.billing.UserBillingWriter;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingConfiguration {

    @Bean
    public BillingExporter runBillingExporter(final RunBillingLoader runBillingLoader,
                                              final GlobalSearchElasticHelper elasticHelper,
                                              final PreferenceManager preferenceManager) {
        return new StreamingBillingExporter<>("Runs Report",
                BillingExportType.RUN,
                runBillingLoader,
                (request, writer) -> new RunBillingWriter(writer, preferenceManager),
                elasticHelper);
    }

    @Bean
    public BillingExporter userBillingExporter(final UserBillingLoader userBillingLoader,
                                               final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("General Users Report",
                BillingExportType.USER,
                userBillingLoader,
                (request, writer) -> new UserBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }

    @Bean
    public BillingExporter billingCenterBillingExporter(final BillingCenterBillingLoader billingCenterBillingLoader,
                                                        final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("General Billing Centers Report",
                BillingExportType.BILLING_CENTER,
                billingCenterBillingLoader,
                (request, writer) -> new BillingCenterBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }

    @Bean
    public BillingExporter storageBillingExporter(final StorageBillingLoader storageBillingLoader,
                                                  final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("Storages Report",
                BillingExportType.STORAGE,
                storageBillingLoader,
                (request, writer) -> new StorageBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }

    @Bean
    public BillingExporter instanceBillingExporter(final InstanceBillingLoader instanceBillingLoader,
                                                   final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("Instances Report",
                BillingExportType.INSTANCE,
                instanceBillingLoader,
                (request, writer) -> new InstanceBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }

    @Bean
    public BillingExporter pipelineBillingExporter(final PipelineBillingLoader pipelineBillingLoader,
                                                   final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("Pipelines Report",
                BillingExportType.PIPELINE,
                pipelineBillingLoader,
                (request, writer) -> new PipelineBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }

    @Bean
    public BillingExporter toolBillingExporter(final ToolBillingLoader toolBillingLoader,
                                               final GlobalSearchElasticHelper elasticHelper) {
        return new StreamingBillingExporter<>("Tools reports",
                BillingExportType.TOOL,
                toolBillingLoader,
                (request, writer) -> new ToolBillingWriter(writer, request.getFrom(), request.getTo()),
                elasticHelper);
    }
}
