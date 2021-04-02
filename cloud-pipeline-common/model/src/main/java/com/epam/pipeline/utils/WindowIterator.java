package com.epam.pipeline.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class WindowIterator<T> implements Iterator<List<T>> {

    private static final double DEFAULT_WINDOW_SIZE_MULTIPLIER = 1.1;
    
    private final Iterator<List<T>> iterator;
    private final int windowSize;
    private final List<T> window;

    public WindowIterator(final Iterator<List<T>> iterator, final int windowSize) {
        this.iterator = iterator;
        this.windowSize = windowSize;
        this.window = new ArrayList<>((int) (windowSize * DEFAULT_WINDOW_SIZE_MULTIPLIER));
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public List<T> next() {
        window.clear();
        while (iterator.hasNext() && window.size() < windowSize) {
            window.addAll(iterator.next());
        }
        return window;
    }
}
