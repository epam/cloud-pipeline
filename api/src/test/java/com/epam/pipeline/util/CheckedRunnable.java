package com.epam.pipeline.util;

@FunctionalInterface
public interface CheckedRunnable {

    void run() throws Exception;
}

