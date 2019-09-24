package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.entity.cluster.AllowedInstanceAndPriceTypes;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.PipelineTask;
import com.epam.pipeline.entity.pipeline.RunLog;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.run.PipelineStart;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.rest.PagedResult;
import com.epam.pipeline.tesadapter.entity.TesTokenHolder;
import com.epam.pipeline.utils.QueryUtils;
import com.epam.pipeline.vo.PagingRunFilterExpressionVO;
import com.epam.pipeline.vo.PagingRunFilterVO;
import com.epam.pipeline.vo.RunStatusVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CloudPipelineAPIClient {
    private TesTokenHolder tesTokenHolder;

    @Value("${cloud.pipeline.host}")
    private String cloudPipelineHostUrl;

    @Autowired
    public CloudPipelineAPIClient(TesTokenHolder tesTokenHolder) {
        this.tesTokenHolder = tesTokenHolder;
    }

    private CloudPipelineAPI buildCloudPipelineAPI(String apiHost, String token) {
        return new CloudPipelineApiBuilder(0, 0, apiHost, token)
                .buildClient();
    }

    public PipelineRun runPipeline(PipelineStart runVo) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .runPipeline(runVo));
    }

    public PipelineRun loadPipelineRun(final Long pipelineRunId) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadPipelineRun(pipelineRunId));
    }

    public PipelineRun updateRunStatus(final Long pipelineRunId, RunStatusVO statusUpdate) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .updateRunStatus(pipelineRunId, statusUpdate));
    }

    public AllowedInstanceAndPriceTypes loadAllowedInstanceAndPriceTypes(final Long toolId, final Long regionId,
                                                                         final Boolean spot) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadAllowedInstanceAndPriceTypes(toolId, regionId, spot));
    }

    public Tool loadTool(String image) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadTool(null, image));
    }

    public List<AbstractCloudRegion> loadAllRegions() {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadAllRegions());
    }

    public List<AbstractDataStorage> loadAllDataStorages() {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadAllDataStorages());
    }

    public List<RunLog> getRunLog(final Long runId) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadLogs(runId));
    }

    public List<PipelineTask> loadPipelineTasks(final Long id) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadPipelineTasks(id));
    }

    public PagedResult<List<PipelineRun>> searchRuns(PagingRunFilterExpressionVO filterVO) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .searchRuns(filterVO));
    }

    public PagedResult<List<PipelineRun>> filterRuns(PagingRunFilterVO filterVO, Boolean loadStorageLinks) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .filterRuns(filterVO, loadStorageLinks));
    }

    public AbstractCloudRegion loadRegion(final Long regionId) {
        return QueryUtils.execute(buildCloudPipelineAPI(cloudPipelineHostUrl, tesTokenHolder.getToken())
                .loadRegion(regionId));
    }
}
