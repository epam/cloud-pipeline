package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

import java.time.LocalDateTime;

@Value
@Wither
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
