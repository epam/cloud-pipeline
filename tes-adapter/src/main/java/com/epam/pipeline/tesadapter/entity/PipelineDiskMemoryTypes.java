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
    EIB("EiB"),
    KIB_TO_GIB("0.00000095367432"),
    MIB_TO_GIB("0.0009765625"),
    GIB_TO_GIB("1"),
    TIB_TO_GIB("1024"),
    PIB_TO_GIB("1048576"),
    EIB_TO_GIB("1073741824");

    private String value;
}
