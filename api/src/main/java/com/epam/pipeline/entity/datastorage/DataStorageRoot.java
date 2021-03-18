package com.epam.pipeline.entity.datastorage;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@Wither
@AllArgsConstructor
public class DataStorageRoot {
    
    Long id;
    String root;
}
