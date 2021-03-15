package com.epam.pipeline.elasticsearchagent.utils;

import java.util.Iterator;
import java.util.List;

public final class IteratorUtils {

    private static final int DEFAULT_CHUNK_SIZE = 100;

    private IteratorUtils() {}

    public static <T> Iterator<List<T>> windowed(final Iterator<List<T>> iterator) {
        return windowed(iterator, DEFAULT_CHUNK_SIZE);
    }

    public static <T> Iterator<List<T>> windowed(final Iterator<List<T>> iterator, final int windowSize) {
        return new WindowIterator<>(iterator, windowSize);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator) {
        return chunked(iterator, DEFAULT_CHUNK_SIZE);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator, final int chunkSize) {
        return new ChunkedIterator<>(iterator, chunkSize);
    }
}
