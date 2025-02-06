package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.dao.pipeline.PipelineRunResultDao;
import com.epam.pipeline.entity.pipeline.run.PipelineRunResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class PipelineRunResultManager {

    @Autowired
    private PipelineRunResultDao pipelineRunResultDao;

    @Transactional(propagation = Propagation.REQUIRED)
    public void addPipelineRunResults(final List<PipelineRunResult> results) {
        results.forEach(PipelineRunResult::validate);
        pipelineRunResultDao.addPipelineRunResults(results);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<PipelineRunResult> loadPipelineRunResultsForRun(final long runId) {
        return pipelineRunResultDao.loadPipelineRunResultsForRun(runId);
    }

}
