package com.epam.pipeline.entity.execution;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ImageSpecificLaunchCommandTemplate {

    /**
     * Image pattern to match to decide if the launch command should be picked for the run.
     * e.g. centos*, library/centos:7, etc
     * */
    String image;

    /**
     * Launch command that would be executed as a pid 1 for a pod if the image matched.
     * e.g. centos*, library/centos:7, etc
     * */
    String command;
}
