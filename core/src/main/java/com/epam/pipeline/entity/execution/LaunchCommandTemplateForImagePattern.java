package com.epam.pipeline.entity.execution;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class LaunchCommandTemplateForImagePattern {
    String image;
    String command;
}
