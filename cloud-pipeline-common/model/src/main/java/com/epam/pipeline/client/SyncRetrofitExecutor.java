package com.epam.pipeline.client;

import com.epam.pipeline.exception.PipelineResponseApiException;
import com.epam.pipeline.exception.PipelineResponseHttpException;
import com.epam.pipeline.exception.PipelineResponseIOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class SyncRetrofitExecutor implements RetrofitExecutor {

    @Override
    public <T> T execute(final Call<T> call) {
        final Response<T> response = request(call);
        if (!response.isSuccessful()) {
            throw new PipelineResponseHttpException(String.format("Unexpected response http code: %d, %s",
                    response.code(), getErrorBody(response).orElse(StringUtils.EMPTY)));
        }
        if (response.body() == null) {
            throw new PipelineResponseApiException("Empty response body");
        }
        return response.body();
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private <T> Response<T> request(final Call<T> call) {
        try {
            return call.execute();
        } catch (Exception e) {
            throw new PipelineResponseIOException(e);
        }
    }

    private <T> Optional<String> getErrorBody(final Response<T> response){
        try (ResponseBody body = response.errorBody()) {
            return Optional.ofNullable(body).flatMap(this::toString);
        }
    }

    private Optional<String> toString(final ResponseBody body) {
        try {
            return Optional.ofNullable(body.string());
        } catch (IOException e) {
            log.warn("Could not extract body from response", e);
            return Optional.empty();
        }
    }
}
