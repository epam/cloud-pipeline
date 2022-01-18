package com.epam.pipeline.billingreportagent.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class EntityDocument {

    String id;
    Map<String, Object> fields;
}
