package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.pipeline.PipelineRunResultDao;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class PipelineRunResultManager {

    @Autowired
    private PipelineRunResultDao pipelineRunResultDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void addPipelineRunResults(final List<PipelineRunResult> results) {
        validate(results);
        pipelineRunResultDao.addPipelineRunResults(results);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRunResult> loadPipelineRunResultsForRun(final long runId) {
        return pipelineRunResultDao.loadPipelineRunResultsForRun(runId);
    }

    private void validate(final List<PipelineRunResult> results) {
        results.forEach(result -> {
            Assert.notNull(result.getRunId(), "Run ID should be provided for run result object!");
            Assert.isTrue(result.getRunId() > 0, "Run ID should be > 0 for run result object!");
            Assert.isTrue(StringUtils.hasText(result.getFileMask()), "File mask should be provided!");
            Assert.notEmpty(result.getItems(), "Items should be provided for PipelineRunResult object!");
            result.getItems().forEach(
                path -> Assert.isTrue(StringUtils.hasText(path), "Item path should not be empty!")
            );
        });
    }

}
