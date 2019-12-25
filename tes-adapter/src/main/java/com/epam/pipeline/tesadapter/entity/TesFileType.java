package com.epam.pipeline.tesadapter.entity;

import lombok.AllArgsConstructor;

/**
 * Gets or Sets tesFileType
 */
@AllArgsConstructor
public enum TesFileType {
    FILE("FILE"),
    DIRECTORY("DIRECTORY");

    private String value;
}

