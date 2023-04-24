package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class DataStorageConvertRequest {

    @JsonProperty("target")
    DataStorageConvertRequestType targetType;

    @JsonProperty("source")
    DataStorageConvertRequestAction sourceAction;
}
