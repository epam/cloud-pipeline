package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.billing.BillingExportType;
import com.epam.pipeline.entity.billing.BillingGrouping;
import com.epam.pipeline.manager.billing.BillingCenterBillingLoader;
import com.epam.pipeline.manager.billing.BillingCenterBillingWriter;
import com.epam.pipeline.manager.billing.BillingExporter;
import com.epam.pipeline.manager.billing.CommonBillingExporter;
import com.epam.pipeline.manager.billing.InstanceBillingLoader;
import com.epam.pipeline.manager.billing.InstanceBillingWriter;
import com.epam.pipeline.manager.billing.PipelineBillingLoader;
import com.epam.pipeline.manager.billing.PipelineBillingWriter;
import com.epam.pipeline.manager.billing.RunBillingLoader;
import com.epam.pipeline.manager.billing.RunBillingWriter;
import com.epam.pipeline.manager.billing.StorageBillingLoader;
import com.epam.pipeline.manager.billing.StorageBillingWriter;
import com.epam.pipeline.manager.billing.ToolBillingLoader;
import com.epam.pipeline.manager.billing.ToolBillingWriter;
import com.epam.pipeline.manager.billing.UserBillingLoader;
import com.epam.pipeline.manager.billing.UserBillingWriter;
import com.epam.pipeline.manager.billing.detail.ComplexBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.EntityBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.MappingBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.PipelineBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.StorageBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.ToolBillingDetailsLoader;
import com.epam.pipeline.manager.billing.detail.UserBillingDetailsLoader;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.pipeline.PipelineManager;
import com.epam.pipeline.manager.pipeline.ToolManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.manager.utils.GlobalSearchElasticHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class BillingConfiguration {

    @Value("${billing.empty.report.value:unknown}")
    private String emptyValue;

    @Value("${billing.center.key}")
    private String billingCenterKey;

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
            (request, writer) -> new StorageBillingWriter(writer, request.getFrom(), request.getTo(),
                    request.getProperties()),
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

    @Bean
    public EntityBillingDetailsLoader pipelineBillingDetailsLoader(
            final PipelineManager pipelineManager,
            final MessageHelper messageHelper) {
        final Map<String, String> mappings = new HashMap<>();
        mappings.put(EntityBillingDetailsLoader.NAME, "pipeline_name");
        mappings.put(EntityBillingDetailsLoader.OWNER, "pipeline_owner_name");
        mappings.put(EntityBillingDetailsLoader.CREATED, "pipeline_created_date");
        return new ComplexBillingDetailsLoader(BillingGrouping.PIPELINE, Arrays.asList(
                new MappingBillingDetailsLoader(BillingGrouping.PIPELINE, mappings),
                new PipelineBillingDetailsLoader(pipelineManager, messageHelper, emptyValue)));
    }

    @Bean
    public EntityBillingDetailsLoader toolBillingDetailsLoader(
            final ToolManager toolManager,
            final MessageHelper messageHelper) {
        final Map<String, String> mappings = new HashMap<>();
        mappings.put(EntityBillingDetailsLoader.NAME, "tool");
        mappings.put(EntityBillingDetailsLoader.OWNER, "tool_owner_name");
        mappings.put(EntityBillingDetailsLoader.CREATED, "tool_created_date");
        return new ComplexBillingDetailsLoader(BillingGrouping.TOOL, Arrays.asList(
                new MappingBillingDetailsLoader(BillingGrouping.TOOL, mappings),
                new ToolBillingDetailsLoader(toolManager, messageHelper, emptyValue)));
    }

    @Bean
    public EntityBillingDetailsLoader storageBillingDetailsLoader(
            final DataStorageManager dataStorageManager,
            final CloudRegionManager regionManager,
            final FileShareMountManager fileShareMountManager,
            final EntityBillingDetailsLoader userBillingDetailsLoader,
            final MessageHelper messageHelper) {
        final Map<String, String> mappings = new HashMap<>();
        mappings.put(EntityBillingDetailsLoader.OWNER, "owner");
        mappings.put(EntityBillingDetailsLoader.CREATED, "storage_created_date");
        mappings.put(EntityBillingDetailsLoader.BILLING_CENTER, "billing_center");
        mappings.put(EntityBillingDetailsLoader.REGION, "cloud_region_name");
        mappings.put(EntityBillingDetailsLoader.PROVIDER, "cloud_region_provider");
        mappings.put(StorageBillingDetailsLoader.STORAGE_NAME, "storage_name");
        mappings.put(StorageBillingDetailsLoader.STORAGE_PATH, "storage_path");
        mappings.put(StorageBillingDetailsLoader.OBJECT_STORAGE_TYPE, "object_storage_type");
        mappings.put(StorageBillingDetailsLoader.FILE_STORAGE_TYPE, "file_storage_type");
        return new ComplexBillingDetailsLoader(BillingGrouping.STORAGE, Arrays.asList(
                new MappingBillingDetailsLoader(BillingGrouping.STORAGE, mappings),
                new StorageBillingDetailsLoader(dataStorageManager, regionManager, fileShareMountManager,
                        userBillingDetailsLoader, messageHelper, emptyValue)));
    }

    @Bean
    public EntityBillingDetailsLoader userBillingDetailsLoader(
            final UserManager userManager,
            final MetadataManager metadataManager,
            final MessageHelper messageHelper) {
        final Map<String, String> mappings = new HashMap<>();
        mappings.put(EntityBillingDetailsLoader.NAME, "owner");
        mappings.put(EntityBillingDetailsLoader.BILLING_CENTER, "billing_center");
        return new ComplexBillingDetailsLoader(BillingGrouping.USER, Arrays.asList(
                new MappingBillingDetailsLoader(BillingGrouping.USER, mappings),
                new UserBillingDetailsLoader(userManager, metadataManager, messageHelper, emptyValue,
                        billingCenterKey)));
    }
}
