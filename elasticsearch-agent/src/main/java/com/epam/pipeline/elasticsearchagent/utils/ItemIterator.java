package com.epam.pipeline.elasticsearchagent.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class ItemIterator<T> implements Iterator<T> {

    private final Iterator<T> iterator;

    public ItemIterator(final Iterator<List<T>> iterator) {
        final Spliterator<List<T>> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        this.iterator = StreamSupport.stream(spliterator, false).flatMap(List::stream).iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public T next() {
        return iterator.next();
    }
}
