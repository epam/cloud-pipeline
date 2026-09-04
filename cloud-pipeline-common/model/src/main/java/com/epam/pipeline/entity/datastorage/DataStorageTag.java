package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import lombok.With;

import java.time.LocalDateTime;

@Value
@With
public class DataStorageTag {

    DataStorageObject object;
    String key;
    String value;
    LocalDateTime createdDate;

    @JsonCreator
    public DataStorageTag(@JsonProperty("object") final DataStorageObject object,
                          @JsonProperty("key") final String key,
                          @JsonProperty("value") final String value,
                          @JsonProperty("createdDate") final LocalDateTime createdDate) {
        this.object = object;
        this.key = key;
        this.value = value;
        this.createdDate = createdDate;
    }

    public DataStorageTag(final DataStorageObject object, final String key, final String value) {
        this(object, key, value, null);
    }
}
