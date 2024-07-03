package com.epam.pipeline.vmmonitor.service;

public interface Notifier<T> {

    void notify(T event);
}
