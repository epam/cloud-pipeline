package com.epam.pipeline.tesadapter.entity;

import com.fasterxml.jackson.annotation.JsonValue;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Gets or Sets tesFileType
 */
public enum TesFileType {
  
  FILE("FILE"),
  
  DIRECTORY("DIRECTORY");

  private String value;

  TesFileType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static TesFileType fromValue(String text) {
    for (TesFileType b : TesFileType.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

