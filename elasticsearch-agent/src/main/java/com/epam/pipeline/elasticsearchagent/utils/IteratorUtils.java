package com.epam.pipeline.elasticsearchagent.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class IteratorUtils {

    private IteratorUtils() {}

    public static <T> Stream<T> streamFrom(final Iterator<T> iterator) {
        final Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator) {
        return chunked(iterator, 100);
    }

    public static <T> Iterator<List<T>> chunked(final Iterator<T> iterator, final int chunkSize) {
        return new ChunkedIterator<>(iterator, chunkSize);
    }

    public static <T> Iterator<T> unchunked(final Iterator<List<T>> iterator) {
        return new ItemIterator<>(iterator);
    }
}
