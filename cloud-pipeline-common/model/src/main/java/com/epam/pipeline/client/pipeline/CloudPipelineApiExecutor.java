package com.epam.pipeline.client.pipeline;

import com.epam.pipeline.rest.Result;
import retrofit2.Call;

public interface CloudPipelineApiExecutor {
    
    <T> T execute(final Call<Result<T>> call);

    String getStringResponse(final Call<byte[]> call);

    byte[] getByteResponse(final Call<byte[]> call);
}
