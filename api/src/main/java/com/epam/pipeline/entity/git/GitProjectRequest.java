package com.epam.pipeline.entity.git;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitProjectRequest {
    private String name;
    private String description;
    private String visibility;
}
