package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RunBilling {
    Long runId;
    String owner;
    String pipeline;
    String tool;
    String instanceType;
    LocalDateTime started;
    LocalDateTime finished;
    Long duration;
    Long cost;
}
