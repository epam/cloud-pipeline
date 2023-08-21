package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@Builder(toBuilder = true)
public class GpuDevice {

    String name;
    String manufacturer;
    Integer cores;

    public static GpuDevice of(final String name, final String manufacturer) {
        return of(name, manufacturer, null);
    }

    public static GpuDevice of(final String name, final String manufacturer, final Integer cores) {
        return new GpuDevice(normalize(name), normalize(manufacturer), cores);
    }

    private static String normalize(final String name) {
        return StringUtils.upperCase(StringUtils.stripToNull(name));
    }

    @JsonIgnore
    public String getManufacturerAndName() {
        return StringUtils.stripToNull(String.format("%s %s",
                StringUtils.stripToEmpty(manufacturer),
                StringUtils.stripToEmpty(name)));
    }
}
