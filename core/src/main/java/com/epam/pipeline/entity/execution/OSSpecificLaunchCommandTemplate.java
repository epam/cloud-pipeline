package com.epam.pipeline.entity.execution;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class OSSpecificLaunchCommandTemplate {

    /**
     * Comma separated list of OS version patterns to match to decide if the launch command should be picked
     * for the run.
     * e.g. 'centos,ubuntu:18' or 'centos:7', etc.
     * */
    String os;

    /**
     * Launch command that would be executed as a pid 1 for a pod if the image matched.
     * */
    String command;
}
