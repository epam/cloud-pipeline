package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;
import com.epam.pipeline.manager.ConvertManager;

public interface DataStorageToSecuredEntityConvertManager
        extends ConvertManager<AbstractDataStorage, DataStorageConvertRequest, AbstractSecuredEntity> {

    DataStorageConvertRequestType getTargetType();

}
