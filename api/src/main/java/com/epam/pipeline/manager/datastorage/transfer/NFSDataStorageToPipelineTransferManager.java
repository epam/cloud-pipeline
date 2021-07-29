package com.epam.pipeline.manager.datastorage.transfer;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageMounter;
import com.epam.pipeline.manager.pipeline.transfer.LocalPathToPipelineTransferManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class NFSDataStorageToPipelineTransferManager
        implements DataStorageToPipelineTransferManager<NFSDataStorage> {

    private final NFSStorageMounter nfsStorageMounter;
    private final LocalPathToPipelineTransferManager localPathToPipelineTransferManager;

    @Override
    public DataStorageType getType() {
        return DataStorageType.NFS;
    }

    @Override
    @SneakyThrows
    public void transfer(final NFSDataStorage storage, final Pipeline pipeline) {
        final Path storagePath = nfsStorageMounter.mount(storage).toPath();
        localPathToPipelineTransferManager.transfer(storagePath, pipeline);
    }

}
