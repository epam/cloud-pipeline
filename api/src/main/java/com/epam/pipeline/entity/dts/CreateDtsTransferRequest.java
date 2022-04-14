package com.epam.pipeline.entity.dts;

import lombok.Value;

@Value
public class CreateDtsTransferRequest {
    CreateDtsTransferRequestStorageItem source;
    CreateDtsTransferRequestStorageItem destination;
}
