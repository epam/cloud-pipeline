package com.epam.pipeline.entity.dts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum DtsStatus {
    OFFLINE(0),
    ONLINE(1);

    private final long id;

    public static Optional<DtsStatus> findById(Long id) {
        return Arrays.stream(values())
                .filter(value -> value.id == id)
                .findFirst();
    }
}
