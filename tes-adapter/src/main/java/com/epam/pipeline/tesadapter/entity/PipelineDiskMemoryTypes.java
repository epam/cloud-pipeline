package com.epam.pipeline.tesadapter.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public enum PipelineDiskMemoryTypes {
    KIB("KiB"),
    GIB("GiB"),
    MIB("MiB"),
    TIB("TiB"),
    PIB("PiB"),
    EIB("EiB");

    private String value;
}
