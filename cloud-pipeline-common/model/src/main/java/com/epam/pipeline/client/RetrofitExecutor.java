package com.epam.pipeline.client;

import retrofit2.Call;

public interface RetrofitExecutor {

    <T> T execute(Call<T> call);
}
