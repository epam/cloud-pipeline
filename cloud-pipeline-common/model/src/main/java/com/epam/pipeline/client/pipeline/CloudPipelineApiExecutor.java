package com.epam.pipeline.client.pipeline;

import com.epam.pipeline.rest.Result;
import okhttp3.ResponseBody;
import retrofit2.Call;

import java.io.InputStream;

public interface CloudPipelineApiExecutor {

    <T> T execute(Call<Result<T>> call);

    String getStringResponse(Call<byte[]> call);

    byte[] getByteResponse(Call<byte[]> call);

    InputStream getResponseStream(Call<ResponseBody> call);
}
