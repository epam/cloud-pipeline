package com.epam.pipeline.client.reporter;

import com.epam.pipeline.entity.reporter.NodeReporterHostStats;
import retrofit2.Call;
import retrofit2.http.GET;

public interface NodeReporterClient {
    @GET("/")
    Call<NodeReporterHostStats> load();
}
