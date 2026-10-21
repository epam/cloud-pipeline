package com.epam.pipeline.controller.vo.search;

public final class ElasticSearchQueryUtils {

    private ElasticSearchQueryUtils() {}

    public static String getEscapedQuery(final String query) {
        final String regex = "(?<!\\\\)/";
        final String replacement = "\\\\/";
        return query.replaceAll(regex, replacement);
    }
}
