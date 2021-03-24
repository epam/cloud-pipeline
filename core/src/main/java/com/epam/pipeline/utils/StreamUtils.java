package com.epam.pipeline.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtils {

    private StreamUtils() {}

    public static <T> Stream<T> from(final Iterator<T> iterator) {
        final Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }

    public static <T> Stream<List<T>> windowed(final Stream<List<T>> stream, final int windowSize) {
        return from(IteratorUtils.windowed(stream.iterator(), windowSize));
    }

    public static <T> Stream<List<T>> chunked(final Stream<T> stream, final int chunkSize) {
        return from(IteratorUtils.chunked(stream.iterator(), chunkSize));
    }
}
