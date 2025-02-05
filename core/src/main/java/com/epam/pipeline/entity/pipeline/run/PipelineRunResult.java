package com.epam.pipeline.entity.pipeline.run;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode
public class PipelineRunResult {

    Long runId;
    String name;
    String fileMask;
    List<String> items;

    public PipelineRunResult(Long runId, String name, String fileMask, List<String> items) {
        this.runId = runId;
        this.name = name;
        this.fileMask = fileMask;
        this.items = ListUtils.emptyIfNull(items);
    }


    public PipelineRunResult(Long runId, String name, String fileMask) {
        this.runId = runId;
        this.name = name;
        this.fileMask = fileMask;
        this.items = new ArrayList<>();
    }

    public void addItem(final String path) {
        items.add(path);
    }

}
