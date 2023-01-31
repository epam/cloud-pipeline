package com.epam.pipeline.utils;

import org.apache.commons.collections4.iterators.PeekingIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class GroupingIterator<T> implements Iterator<List<T>> {

    private final PeekingIterator<T> iterator;
    private final Comparator<T> comparator;
    private final List<T> group;

    private T head;

    public GroupingIterator(Iterator<T> iterator, Comparator<T> comparator) {
        this.iterator = new PeekingIterator<>(iterator);
        this.comparator = comparator;
        this.group = new ArrayList<>();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public List<T> next() {
        group.clear();
        if (hasNext()) {
            head = iterator.next();
            group.add(head);
        }
        while (iterator.hasNext() && comparator.compare(head, iterator.peek()) == 0) {
            group.add(iterator.next());
        }
        return group;
    }
}
