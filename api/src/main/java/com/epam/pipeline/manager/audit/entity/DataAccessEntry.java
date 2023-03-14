package com.epam.pipeline.manager.audit.entity;

import lombok.Value;

@Value
public class DataAccessEntry {

    String storage;
    String path;
    DataAccessEntryType type;
}
