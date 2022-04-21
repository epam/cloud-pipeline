package com.epam.pipeline.entity.dts;

import lombok.Value;

@Value
public class CreateDtsDeletionRequestStorageItem {
    String path;
    DtsTaskStorageType type;
}
