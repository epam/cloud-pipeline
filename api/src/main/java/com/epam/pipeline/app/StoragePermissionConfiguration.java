package com.epam.pipeline.app;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.azure.AzureBlobStorage;
import com.epam.pipeline.entity.datastorage.gcp.GSBucketStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.manager.cloud.aws.S3TemporaryCredentialsGenerator;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.manager.datastorage.providers.StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.datastorage.providers.azure.AzureBlobStorageProvider;
import com.epam.pipeline.manager.datastorage.security.SecuredStorageProvider;
import com.epam.pipeline.manager.datastorage.providers.gcp.GSBucketStorageProvider;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageMounter;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider;
import com.epam.pipeline.manager.datastorage.security.StoragePermissionProviderManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoragePermissionConfiguration {

    private final boolean storagePathPermissionsEnabled;

    public StoragePermissionConfiguration(@Value("${data.storage.enable.security}")
                                            final boolean storagePathPermissionsEnabled) {
        this.storagePathPermissionsEnabled = storagePathPermissionsEnabled;
    }

    @Bean
    public StorageProvider<S3bucketDataStorage> s3StorageProvider(
            final AuthManager autManager,
            final MessageHelper messageHelper,
            final CloudRegionManager cloudRegionManager,
            final PreferenceManager preferenceManager,
            final S3TemporaryCredentialsGenerator stsCredentialsGenerator,
            final StoragePermissionProviderManager storagePermissionProviderManager) {
        return secured(new S3StorageProvider(autManager, messageHelper, cloudRegionManager, preferenceManager,
                stsCredentialsGenerator), storagePermissionProviderManager);
    }

    @Bean
    public StorageProvider<AzureBlobStorage> azureStorageProvider(
            final AuthManager autManager,
            final MessageHelper messageHelper,
            final CloudRegionManager cloudRegionManager,
            final StoragePermissionProviderManager storagePermissionProviderManager) {
        return secured(new AzureBlobStorageProvider(cloudRegionManager, messageHelper, autManager),
                storagePermissionProviderManager);
    }

    @Bean
    public StorageProvider<GSBucketStorage> gsStorageProvider(
            final CloudRegionManager cloudRegionManager,
            final MessageHelper messageHelper,
            final GCPClient gcpClient,
            final AuthManager authManager,
            final StoragePermissionProviderManager storagePermissionProviderManager) {
        return secured(new GSBucketStorageProvider(cloudRegionManager, messageHelper, gcpClient, authManager),
                storagePermissionProviderManager);
    }

    @Bean
    public StorageProvider<NFSDataStorage> nfsStorageProvider(
            final MessageHelper messageHelper,
            final PreferenceManager preferenceManager,
            final FileShareMountManager fileShareMountManager,
            final NFSStorageMounter nfsStorageMounter,
            final StoragePermissionProviderManager storagePermissionProviderManager) {
        return secured(new NFSStorageProvider(messageHelper, preferenceManager, fileShareMountManager,
                nfsStorageMounter), storagePermissionProviderManager);
    }

    private <T extends AbstractDataStorage> StorageProvider<T> secured(
            final StorageProvider<T> provider,
            final StoragePermissionProviderManager storagePermissionProviderManager) {
        return storagePathPermissionsEnabled
                ? new SecuredStorageProvider<>(provider, storagePermissionProviderManager)
                : provider;
    }

}
