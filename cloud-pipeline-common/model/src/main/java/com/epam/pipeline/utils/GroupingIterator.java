package com.epam.pipeline.utils;

import org.apache.commons.collections4.iterators.PeekingIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class GroupingIterator<T> implements Iterator<List<T>> {

    private final PeekingIterator<T> iterator;
    private final Comparator<T> comparator;

    public GroupingIterator(final Iterator<T> iterator, final Comparator<T> comparator) {
        this.iterator = new PeekingIterator<>(iterator);
        this.comparator = comparator;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public List<T> next() {
        final List<T> group = new ArrayList<>();
        T head = iterator.next();
        group.add(head);
        while (iterator.hasNext() && comparator.compare(head, iterator.peek()) == 0) {
            group.add(iterator.next());
        }
        return group;
    }
}
