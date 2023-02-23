package com.epam.pipeline.manager.utils;

public final class ElasticSearchUtils {

    public static final String ES_WILDCARD = "*";
    public static final String ES_DOC_FIELDS_SEPARATOR = ".";
    public static final String ES_DOC_AGGS_SEPARATOR = ">";
    public static final String ES_ELEMENTS_SEPARATOR = ",";

    private ElasticSearchUtils() {}

    public static String fieldsPath(final String... paths) {
        return bucketsPath(ES_DOC_FIELDS_SEPARATOR, paths);
    }

    public static String bucketsPath(final String separator, final String[] paths) {
        return String.join(separator, paths);
    }

}
