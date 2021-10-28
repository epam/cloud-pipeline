package com.epam.release.notes.agent.service;

import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.IOException;

public interface RestApiClient {

    default  <R> R execute(Call<R> call) {
        try {
            Response<R> response = call.execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw new HttpException(response);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
