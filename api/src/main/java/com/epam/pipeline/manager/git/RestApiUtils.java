package com.epam.pipeline.manager.git;

import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.exception.git.UnexpectedResponseStatusException;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

final public class RestApiUtils {

    private RestApiUtils() {
    }

    public static  <R> R execute(Call<R> call) throws GitClientException {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new UnexpectedResponseStatusException(HttpStatus.valueOf(response.code()),
                        response.errorBody() != null ? response.errorBody().string() : "");
            }
        } catch (IOException e) {
            throw new GitClientException(e.getMessage(), e);
        }
    }

}
