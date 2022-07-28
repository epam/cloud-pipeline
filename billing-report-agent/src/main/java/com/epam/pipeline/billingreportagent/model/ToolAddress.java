package com.epam.pipeline.billingreportagent.model;

import lombok.Value;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

@Value
public class ToolAddress {

    private static final String COMMON_DELIMITER = "/";
    private static final String VERSION_DELIMITER = ":";

    String registry;
    String group;
    String tool;
    String version;

    public static ToolAddress from(final String image) {
        final String[] imageItems = image.split(COMMON_DELIMITER);
        final String registry = imageItems.length > 0 ? imageItems[0] : null;
        final String group = imageItems.length > 1 ? imageItems[1] : null;
        final String toolAndVersion = imageItems.length > 2 ? join(imageItems, 2) : null;
        final String[] toolAndVersionItems = toolAndVersion != null
                ? toolAndVersion.split(VERSION_DELIMITER)
                : new String[0];
        final String tool = toolAndVersionItems.length > 0 ? toolAndVersionItems[0] : null;
        final String version = toolAndVersionItems.length > 1 ? join(toolAndVersionItems, 1) : null;
        return new ToolAddress(registry, group, tool, version);
    }

    private static String join(final String[] elements, final int startInclusive) {
        return String.join(COMMON_DELIMITER, ArrayUtils.subarray(elements, startInclusive, elements.length));
    }

    public static ToolAddress empty() {
        return new ToolAddress(null, null, null, null);
    }

    public String getPathWithoutVersion() {
        return String.join(COMMON_DELIMITER,
                StringUtils.defaultIfBlank(registry, StringUtils.EMPTY),
                StringUtils.defaultIfBlank(group, StringUtils.EMPTY),
                StringUtils.defaultIfBlank(tool, StringUtils.EMPTY));
    }
}
