package com.epam.pipeline.entity.dts;

import lombok.Value;

import java.time.LocalDateTime;

@Value
public class CreateDtsDeletionRequest {
    CreateDtsDeletionRequestStorageItem target;
    LocalDateTime scheduled;
}
