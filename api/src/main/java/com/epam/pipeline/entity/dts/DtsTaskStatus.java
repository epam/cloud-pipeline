package com.epam.pipeline.entity.dts;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DtsTaskStatus {

    CREATED(false), RUNNING(false), SUCCESS(true), FAILURE(true), STOPPED(false);

    private final boolean finalStatus;
}

