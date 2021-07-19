package com.epam.pipeline.dts.common.service.impl;

import com.epam.pipeline.dts.common.service.IdentificationService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SimpleIdentificationService implements IdentificationService {

    private final String id;

    @Override
    public String getId() {
        return id;
    }
}
