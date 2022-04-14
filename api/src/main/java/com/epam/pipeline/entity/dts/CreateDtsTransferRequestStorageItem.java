package com.epam.pipeline.entity.dts;

import lombok.Value;

@Value
public class CreateDtsTransferRequestStorageItem {
    String path;
    DtsTransferStorageType type;
}
