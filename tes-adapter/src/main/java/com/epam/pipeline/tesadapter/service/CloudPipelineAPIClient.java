package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.utils.QueryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CloudPipelineAPIClient {
    private CloudPipelineAPI cloudPipelineAPI;

    public CloudPipelineAPIClient(@Value("${cloud.pipeline.host}") String cloudPipelineHostUrl,
                                  @Value("${cloud.pipeline.token}") String cloudPipelineToken) {
        this.cloudPipelineAPI =
                new CloudPipelineApiBuilder(0, 0, cloudPipelineHostUrl, cloudPipelineToken)
                        .buildClient();
    }

    public PipelineRun loadPipelineRun(final Long pipelineRunId) {
        return QueryUtils.execute(cloudPipelineAPI.loadPipelineRun(pipelineRunId));
    }
}
