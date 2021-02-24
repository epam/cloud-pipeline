package com.epam.pipeline.elasticsearchagent.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChunkedIterator<T> implements Iterator<List<T>> {

    private final Iterator<T> iterator;
    private final int chunkSize;
    private final List<T> chunk;

    public ChunkedIterator(final Iterator<T> iterator, final int chunkSize) {
        this.iterator = iterator;
        this.chunkSize = chunkSize;
        this.chunk = new ArrayList<>(chunkSize);
    }
    
    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public List<T> next() {
        chunk.clear();
        while (iterator.hasNext() && chunk.size() < chunkSize) {
            chunk.add(iterator.next());
        }
        return chunk;
    }
}
