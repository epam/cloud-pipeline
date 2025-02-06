package com.epam.pipeline.entity.pipeline.run;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.collections4.ListUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

    @JsonIgnore
    public void addItem(final String path) {
        items.add(path);
    }

    public void validate() {
        Assert.notNull(this.getRunId(), "Run ID should be provided for run result object!");
        Assert.isTrue(this.getRunId() > 0, "Run ID should be > 0 for run result object!");
        Assert.isTrue(StringUtils.hasText(this.getFileMask()), "File mask should be provided!");
        Assert.notEmpty(this.getItems(), "Items should be provided for PipelineRunResult object!");
        this.getItems().forEach(
                path -> Assert.isTrue(StringUtils.hasText(path), "Item path should not be empty!")
        );
    }

}
