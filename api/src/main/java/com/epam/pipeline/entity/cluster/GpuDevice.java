/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.entity.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@Builder(toBuilder = true)
public class GpuDevice {

    private static final String A100_80GB_TYPE = "80GB";
    private static final String A100 = "A100";

    String name;
    String manufacturer;
    Integer cores;

    public static GpuDevice from(final String[] items) {
        final String manufacturer = items[0];
        final String name = items[items.length - 1];
        if (items.length < 3) {
            return from(name, manufacturer);
        }
        final String gpuFamily = items[items.length - 2];
        final String fullName = A100_80GB_TYPE.equalsIgnoreCase(name) && A100.equalsIgnoreCase(gpuFamily)
                ? String.format("%s-%s", gpuFamily, name)
                : name;
        return from(fullName, manufacturer);
    }

    public static GpuDevice from(final String name, final String manufacturer) {
        return from(name, manufacturer, null);
    }

    public static GpuDevice from(final String name, final String manufacturer, final Integer cores) {
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
