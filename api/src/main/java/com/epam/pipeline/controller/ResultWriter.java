package com.epam.pipeline.controller;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ResultWriter {

    @Getter
    private final String name;
    private final IOConsumer<OutputStream> consumer;

    public static ResultWriter checked(final String name, final IOConsumer<OutputStream> consumer) {
        return new ResultWriter(name, consumer);
    }

    public static ResultWriter unchecked(final String name, final Consumer<OutputStream> consumer) {
        return checked(name, consumer::accept);
    }

    public void write(final HttpServletResponse response) throws IOException {
        write(response.getOutputStream());
    }

    public void write(final OutputStream os) throws IOException {
        consumer.accept(os);
    }

    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

}
