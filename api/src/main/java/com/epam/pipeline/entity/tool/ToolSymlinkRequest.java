package com.epam.pipeline.entity.tool;

import lombok.Value;

@Value
public class ToolSymlinkRequest {
    Long toolId;
    Long groupId;
}
