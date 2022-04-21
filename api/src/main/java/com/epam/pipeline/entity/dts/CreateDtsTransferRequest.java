package com.epam.pipeline.entity.dts;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class CreateDtsTransferRequest {
    CreateDtsTransferRequestStorageItem source;
    CreateDtsTransferRequestStorageItem destination;
    LocalDateTime deleteDestinationOn;
}
