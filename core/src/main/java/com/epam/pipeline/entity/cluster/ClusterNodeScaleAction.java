package com.epam.pipeline.entity.cluster;

import com.epam.pipeline.entity.pipeline.run.RunScheduledAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class ClusterNodeScaleAction {
    private ClusterNodeScale clusterNodeScale;
    private RunScheduledAction action;
    private String user;
}
