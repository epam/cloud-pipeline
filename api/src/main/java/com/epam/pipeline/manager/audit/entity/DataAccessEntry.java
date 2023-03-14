package com.epam.pipeline.manager.audit.entity;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class DataAccessEntry {

    String path;
    DataAccessEntryType type;
    LocalDateTime timestamp;
    String user;
}
