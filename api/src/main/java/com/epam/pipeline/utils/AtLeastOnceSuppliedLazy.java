package com.epam.pipeline.utils;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@RequiredArgsConstructor
public class AtLeastOnceSuppliedLazy<T> implements Lazy<T> {

    private final Supplier<T> supplier;
    private T value;

    @Override
    public T get() {
        return value != null ? value : compute();
    }

    private T compute() {
        value = supplier.get();
        return value;
    }

    @SuppressWarnings("PMD.ShortMethodName")
    public static <T> Lazy<T> of(final Supplier<T> supplier) {
        return new AtLeastOnceSuppliedLazy<>(supplier);
    }
}
