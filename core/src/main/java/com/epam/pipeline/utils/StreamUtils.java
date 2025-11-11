package com.epam.pipeline.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
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

    public static <T> Stream<T> appended(final Stream<T> stream, final T item) {
        return Stream.concat(stream, Stream.of(item));
    }

    /**
     * Provides predicate that helps to filter objects in a stream distinctly by key
     * */
    public static <T> Predicate<T> distinctByKeyPredicate(final Function<? super T, ?> keyExtractor) {
        final Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static <T> Stream<T> takeWhile(final Stream<T> stream, final Predicate<T> predicate) {
        return from(IteratorUtils.takeWhile(stream.iterator(), predicate));
    }

}
