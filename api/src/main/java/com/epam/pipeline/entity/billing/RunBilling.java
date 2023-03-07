package com.epam.pipeline.entity.billing;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RunBilling {
    Long runId;
    String owner;
    String billingCenter;
    String pipeline;
    String tool;
    String instanceType;
    String computeType;
    LocalDateTime started;
    LocalDateTime finished;
    Long duration;
    Long cost;
    Long diskCost;
    Long computeCost;
}
