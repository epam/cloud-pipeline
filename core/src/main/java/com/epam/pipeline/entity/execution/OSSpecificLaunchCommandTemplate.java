package com.epam.pipeline.entity.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * @param os         Comma separated list of OS version patterns to match to decide if the launch command
 *                   should be picked for the run.
 *                   e.g. 'centos,ubuntu:18' or 'centos:7', etc.
 * @param docker     Comma separated list of docker images to apply settings, has higher priority than os,
 *                   supported formats: tool, tool:latest
 * @param command    Launch command that would be executed as a pid 1 for a pod if the image matched.
 * @param entrypoint Overrides default docker entry point '/bin/bash'
 * @param args       Sets arguments for docker entrypoint
 */
@Builder
public record OSSpecificLaunchCommandTemplate(
        @JsonProperty("os") String os,
        @JsonProperty("docker") String docker,
        @JsonProperty("command") String command,
        @JsonProperty("entrypoint") String entrypoint,
        @JsonProperty("args") List<String> args) {
}
