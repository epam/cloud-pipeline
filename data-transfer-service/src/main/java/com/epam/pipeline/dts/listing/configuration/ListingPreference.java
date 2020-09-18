package com.epam.pipeline.dts.listing.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("dts.listing")
public class ListingPreference {
    private String listScript;
    private String listCommand;
}
