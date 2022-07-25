package com.epam.pipeline.utils;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@RequiredArgsConstructor
public class ThreadSafeLazy<T> implements Lazy<T> {

    private final Supplier<T> supplier;
    private T object;

    @Override
    public T get() {
        return object != null ? object : compute();
    }

    private synchronized T compute() {
        return object = supplier.get();
    }

    public static <T> Lazy<T> of(final Supplier<T> supplier) {
        return new ThreadSafeLazy<>(supplier);
    }
}
