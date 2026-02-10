package com.epam.pipeline.entity.pipeline.run;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PipelineRunWithEngineTasks {
    private final PipelineRun run;
    private final List<String> engineTaskKeys;
}
