package com.epam.pipeline.utils;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Iterator;

public class ZipIterator<L, R> implements Iterator<Pair<L, R>> {

    private final Iterator<L> literator;
    private final Iterator<R> riterator;

    public ZipIterator(final Iterator<L> literator, final Iterator<R> riterator) {
        this.literator = literator;
        this.riterator = riterator;
    }

    @Override
    public boolean hasNext() {
        return literator.hasNext() && riterator.hasNext();
    }

    @Override
    public Pair<L, R> next() {
        return Pair.of(literator.next(), riterator.next());
    }

}
