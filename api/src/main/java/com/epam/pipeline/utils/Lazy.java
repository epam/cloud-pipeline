package com.epam.pipeline.utils;

import java.util.function.Supplier;

public interface Lazy<T> {

    T get();

    @SuppressWarnings("PMD.ShortMethodName")
    static <T> Lazy<T> of(final Supplier<T> supplier) {
        return AtLeastOnceSuppliedLazy.of(supplier);
    }
}
