package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

@Value
@With
@AllArgsConstructor
public class DataStorageTag {

    DataStorageObject object;
    String key;
    String value;
    LocalDateTime createdDate;

    public DataStorageTag(final DataStorageObject object, final String key, final String value) {
        this(object, key, value, null);
    }
}
