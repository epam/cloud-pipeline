package com.epam.pipeline.entity.datastorage;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DataStorageConvertRequest(
        @JsonProperty("target") DataStorageConvertRequestType targetType,
        @JsonProperty("source") DataStorageConvertRequestAction sourceAction) {}
