package com.epam.pipeline.client.pipeline;

import com.epam.pipeline.rest.Result;
import retrofit2.Call;

public interface CloudPipelineApiExecutor {
    
    <T> T execute(Call<Result<T>> call);

    String getStringResponse(Call<byte[]> call);

    byte[] getByteResponse(Call<byte[]> call);
}
