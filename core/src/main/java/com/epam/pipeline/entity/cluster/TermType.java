package com.epam.pipeline.entity.cluster;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
enum TermType {
    ON_DEMAND("OnDemand"),
    SPOT("Spot"),
    LOW_PRIORITY("LowPriority"),
    PREEMPTIBLE("Preemptible");

    String name;
}