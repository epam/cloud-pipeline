package com.epam.pipeline.client.pipeline;

import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.exception.PipelineResponseApiException;
import com.epam.pipeline.exception.PipelineResponseException;
import com.epam.pipeline.exception.PipelineResponseHttpException;
import com.epam.pipeline.exception.PipelineResponseIOException;
import com.epam.pipeline.rest.Result;
import com.epam.pipeline.rest.ResultStatus;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
// Some strange PMD behaviour
@SuppressWarnings("PMD.UnusedPrivateMethod")
public class RetryingCloudPipelineApiExecutor implements CloudPipelineApiExecutor {

    private static final int DEFAULT_RETRY_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);
    public static final int NOT_FOUND = 404;

    private final int attempts;
    private final Duration delay;

    public RetryingCloudPipelineApiExecutor() {
        this(DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_DELAY);
    }
    
    public static RetryingCloudPipelineApiExecutor basic() {
        return new RetryingCloudPipelineApiExecutor();
    }

    @Override
    public <T> T execute(final Call<Result<T>> call) {
        return execute(call, this::internalExecute);
    }

    @SneakyThrows
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private <T, R> R execute(final Call<T> call,
                             final Caller<T, R> caller) {
        if (attempts < 1) {
            throw new IllegalArgumentException("The number of retry attempts should be at least 1.");
        }
        int attempt = 0;
        Exception lastException = null;
        while (attempt < attempts) {
            attempt += 1;
            try {
                return caller.apply(attempt > 1 ? call.clone() : call);
            } catch (PipelineResponseException e) {
                log.error("Cloud Pipeline API call {}/{} has failed due to API error. " +
                        "It won't be retried.", attempt, attempts, e);
                throw e;
            } catch (ObjectNotFoundException e) {
                log.error("Cloud Pipeline API call responded with 404.");
                throw e;
            } catch (Exception e) {
                log.error("Cloud Pipeline API call {}/{} has failed due to IO error. " +
                        "It will be retried in {} s.", attempt, attempts, delay.getSeconds(), e);
                lastException = e;
                Thread.sleep(delay.toMillis());
            }
        }
        throw new PipelineResponseIOException(lastException);
    }

    private <T> T internalExecute(final Call<Result<T>> call) throws IOException {
        final Response<Result<T>> response = call.execute();
        if (!response.isSuccessful()) {
            if (response.code() == NOT_FOUND) {
                throw new ObjectNotFoundException(response.message());
            }
            throw new PipelineResponseHttpException(String.format("Unexpected response http code: %d, %s",
                    response.code(), response.errorBody() != null ? response.errorBody().string() : StringUtils.EMPTY));
        }
        if (response.body() == null) {
            throw new PipelineResponseApiException("Empty response body");
        }
        if (response.body().getStatus() != ResultStatus.OK) {
            throw new PipelineResponseApiException(String.format("Unexpected response api status: %s, %s",
                    response.body().getStatus(), response.body().getMessage()));
        }
        return response.body().getPayload();
    }
    
    @Override
    public String getStringResponse(final Call<byte[]> call) {
        return execute(call, this::internalGetStringResponse);
    }

    private String internalGetStringResponse(final Call<byte[]> call) throws IOException {
        try {
            final Response<byte[]> response = call.execute();
            validateResponseStatus(response);
            return getFileContent(response);
        } catch (JsonMappingException e) {
            // case with empty output
            return null;
        }
    }
    
    @Override
    public byte[] getByteResponse(final Call<byte[]> call) {
        return execute(call, this::internalGetByteResponse);
    }

    @Override
    public InputStream getResponseStream(final Call<ResponseBody> call) {
        try {
            final Response<ResponseBody> response = call.execute();
            validateResponseStatus(response);
            return response.body().byteStream();
        } catch (IOException e) {
            throw new PipelineResponseIOException(e);
        }
    }

    @Override
    public Response<ResponseBody> getResponse(final Call<ResponseBody> call) {
        try {
            final Response<ResponseBody> response = call.execute();
            validateResponseStatus(response);
            return response;
        } catch (IOException e) {
            throw new PipelineResponseIOException(e);
        }
    }

    private byte[] internalGetByteResponse(final Call<byte[]> call) throws IOException {
        try {
            final Response<byte[]> response = call.execute();
            validateResponseStatus(response);
            return response.body();
        } catch (JsonMappingException e) {
            // case with empty output
            return null;
        }
    }

    private void validateResponseStatus(final Response<?> response) throws IOException {
        if (!response.isSuccessful()) {
            if (response.code() == NOT_FOUND) {
                throw new ObjectNotFoundException(response.message());
            }
            throw new PipelineResponseException(String.format("Unexpected status code: %d, %s", response.code(),
                    response.errorBody() != null ? response.errorBody().string() : ""));
        }
    }

    private String getFileContent(final Response<byte[]> response) throws IOException {
        final InputStream in = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            return IOUtils.toString(br);
        }
    }

    @FunctionalInterface
    public interface Caller<T, R> {

        R apply(Call<T> t) throws IOException;
    }

}
