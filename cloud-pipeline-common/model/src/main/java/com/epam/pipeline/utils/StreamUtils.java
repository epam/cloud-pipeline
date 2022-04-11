package com.epam.pipeline.utils;

import org.apache.commons.lang3.tuple.Pair;

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

    public static <L, R> Stream<Pair<L, R>> zipped(final Stream<L> lstream, final Stream<R> rstream) {
        return from(IteratorUtils.zipped(lstream.iterator(), rstream.iterator()));
    }

    /**
     * Inserts a separator between the elements of a stream.
     *
     * interleave(Stream.of(1, 2, 3), 0) = Stream.of(1, 0, 2, 0, 3)
     *
     * @param stream the stream to insert a separator to.
     * @param separator the separator to insert.
     * @param <T> the stream elements type.
     * @return the stream with a separator inserted between all elements
     */
    public static <T> Stream<T> interspersed(final Stream<T> stream, final T separator) {
        return stream
                .flatMap(item -> Stream.of(separator, item))
                .skip(1L);
    }
}
