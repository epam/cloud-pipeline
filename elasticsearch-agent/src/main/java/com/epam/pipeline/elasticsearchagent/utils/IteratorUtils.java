package com.epam.pipeline.elasticsearchagent.utils;

import java.util.Iterator;
import java.util.List;

public final class IteratorUtils {

    private IteratorUtils() {}

    public static <T> Iterator<List<T>> windowed(final Iterator<List<T>> iterator) {
        return windowed(iterator, 100);
    }

    public static <T> Iterator<List<T>> windowed(final Iterator<List<T>> iterator, final int chunkSize) {
        return new WindowIterator<>(iterator, chunkSize);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator) {
        return chunked(iterator, 100);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator, final int chunkSize) {
        return new ChunkedIterator<>(iterator, chunkSize);
    }
}
