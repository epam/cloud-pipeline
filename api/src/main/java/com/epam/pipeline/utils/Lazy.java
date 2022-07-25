package com.epam.pipeline.utils;

import java.util.function.Supplier;

public interface Lazy<T> {

    T get();

    static <T> Lazy<T> of(final Supplier<T> supplier) {
        return ThreadSafeLazy.of(supplier);
    }
}

