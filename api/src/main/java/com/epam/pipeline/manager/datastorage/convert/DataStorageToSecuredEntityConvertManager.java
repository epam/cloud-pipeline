package com.epam.pipeline.manager.datastorage.convert;

import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequest;
import com.epam.pipeline.entity.datastorage.DataStorageConvertRequestType;

/**
 * Convert manager performs conversion of a source data storage object according to a request.
 */
public interface DataStorageToSecuredEntityConvertManager {

    DataStorageConvertRequestType getTargetType();

    /**
     * Converts a source data storage to some secured entity according to a request.
     *
     * @param source data storage to be converted.
     * @param request of a conversion to be performed.
     * @return source data storage to a requested secured entity.
     */
    AbstractSecuredEntity convert(AbstractDataStorage source, DataStorageConvertRequest request);

}
