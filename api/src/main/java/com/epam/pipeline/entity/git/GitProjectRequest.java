package com.epam.pipeline.entity.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitProjectRequest {
    private String name;
    private String path;
    private String description;
    private String visibility;
}
