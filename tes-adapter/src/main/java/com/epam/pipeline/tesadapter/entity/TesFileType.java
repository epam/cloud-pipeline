package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * Gets or Sets tesFileType
 */
@AllArgsConstructor
@ToString
public enum TesFileType {
    FILE("FILE"),
    DIRECTORY("DIRECTORY");

    private String value;
}

