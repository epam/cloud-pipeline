package com.epam.pipeline.entity.execution;

import lombok.Builder;
import lombok.Value;

import java.util.List;

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
     * Comma separated list of docker images to apply settings, has higher priority than os,
     * supported formats: tool, tool:latest
     */
    String docker;

    /**
     * Launch command that would be executed as a pid 1 for a pod if the image matched.
     * */
    String command;

    /**
     * Overrides default docker entry point '/bin/bash'
     */
    String entrypoint;

    /**
     * Sets arguments for docker entrypoint
     */
    List<String> args;
}
