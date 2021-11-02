package com.epam.pipeline.manager.datastorage.transfer;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.TransferManager;

public interface DataStorageToPipelineTransferManager extends TransferManager<AbstractDataStorage, Pipeline> {

    DataStorageType getType();
}
