package com.epam.pipeline.entity.datastorage.access;

import lombok.Value;

@Value
public class DataAccessEvent {

    String storage;
    String path;
    DataAccessEventType type;
}
