package com.epam.pipeline.entity.pipeline.run.runtime.nextflow;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class NextflowTraceFile {
    private final Map<String, NextflowTask> tasks;
}
