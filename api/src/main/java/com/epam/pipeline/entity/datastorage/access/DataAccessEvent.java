package com.epam.pipeline.entity.datastorage.access;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import lombok.Value;

@Value
public class DataAccessEvent {

    String path;
    DataAccessType type;
    AbstractDataStorage storage;
}
