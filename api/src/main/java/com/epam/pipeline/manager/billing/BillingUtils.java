/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.billing;

import com.epam.pipeline.config.Constants;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.metadata.MetadataManager;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class BillingUtils {

    public static final int FALLBACK_EXPORT_AGGREGATION_PAGE_SIZE = 5000;
    public static final int FALLBACK_EXPORT_PERIOD_AGGREGATION_PAGE_SIZE = 1000;
    public static final char SEPARATOR = ',';

    public static final String YEAR_MONTH_FORMAT = "MMMM yyyy";
    public static final DateTimeFormatter ELASTIC_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.ELASTIC_DATE_TIME_FORMAT, Locale.US);
    public static final DateTimeFormatter EXPORT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(Constants.EXPORT_DATE_TIME_FORMAT, Locale.US);
    public static final DateTimeFormatter YEAR_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern(YEAR_MONTH_FORMAT, Locale.US);
    public static final int DECIMAL_SCALE = 2;
    public static final long DURATION_DIVISOR = TimeUnit.MINUTES.convert(NumberUtils.LONG_ONE, TimeUnit.HOURS);
    public static final Long PERCENT_TO_DECIMAL_DIVISOR = tenInPowerOf(2);
    public static final long COST_DIVISOR = tenInPowerOf(4);
    public static final long VOLUME_DIVISOR = tenInPowerOf(9);

    public static final String USER_COLUMN = "User";
    public static final String OWNER_COLUMN = "Owner";
    public static final String BILLING_CENTER_COLUMN = "Billing center";
    public static final String RUN_COLUMN = "Run";
    public static final String INSTANCE_COLUMN = "Instance";
    public static final String PIPELINE_COLUMN = "Pipeline";
    public static final String TOOL_COLUMN = "Tool";
    public static final String STORAGE_COLUMN = "Storage";
    public static final String TYPE_COLUMN = "Type";
    public static final String REGION_COLUMN = "Region";
    public static final String PROVIDER_COLUMN = "Provider";
    public static final String CREATED_COLUMN = "Created";
    public static final String STARTED_COLUMN = "Started";
    public static final String FINISHED_COLUMN = "Finished";
    public static final String RUNS_COUNT_COLUMN = "Runs (count)";
    public static final String RUNS_DURATIONS_COLUMN = "Runs durations (hours)";
    public static final String RUNS_COSTS_COLUMN = "Runs costs ($)";
    public static final String STORAGES_COSTS_COLUMN = "Storage costs ($)";
    public static final String DURATION_COLUMN = "Duration (hours)";
    public static final String COST_COLUMN = "Cost ($)";
    public static final String DETAILED_COST_COLUMN = "Cost, %s ($)";
    public static final String DETAILED_OV_COST_COLUMN = "Cost, %s Old Versions ($)";
    public static final String DISK_COST_COLUMN = "Disk cost ($)";
    public static final String COMPUTE_COST_COLUMN = "Compute cost ($)";
    public static final String AVERAGE_VOLUME_COLUMN = "Average Volume (GB)";
    public static final String DETAILED_AVERAGE_VOLUME_COLUMN = "Average Volume, %s (GB)";
    public static final String DETAILED_OV_AVERAGE_VOLUME_COLUMN = "Average Volume, %s Old Versions (GB)";
    public static final String CURRENT_VOLUME_COLUMN = "Current Volume (GB)";
    public static final String DETAILED_CURRENT_VOLUME_COLUMN = "Current Volume, %s (GB)";
    public static final String DETAILED_OV_CURRENT_VOLUME_COLUMN = "Current Volume, %s Old Versions (GB)";

    public static final String SYNTHETIC_TOTAL_BILLING = "Grand total";
    public static final String MISSING_VALUE = "unknown";
    public static final String DOC_TYPE = "doc_type";
    public static final String COST_FIELD = "cost";
    public static final String DISK_COST_FIELD = "disk_cost";
    public static final String COMPUTE_COST_FIELD = "compute_cost";
    public static final String ACCUMULATED_COST = "accumulatedCost";
    public static final String ACCUMULATED_DISK_COST = "accumulatedDiskCost";
    public static final String ACCUMULATED_COMPUTE_COST = "accumulatedComputeCost";
    public static final String RUN_USAGE_AGG = "usage_runs";
    public static final String STORAGE_USAGE_AGG = "usage_storages";
    public static final String RUN_USAGE_FIELD = "usage_minutes";
    public static final String STORAGE_GROUPING_AGG = "storage_buckets";
    public static final String SINGLE_STORAGE_USAGE_AGG = "usage_storage";
    public static final String TOTAL_STORAGE_USAGE_AGG = "usage_storages";
    public static final String LAST_STORAGE_USAGE_VALUE = "usage_storages_last";
    public static final String STORAGE_USAGE_FIELD = "usage_bytes";
    public static final String LAST_BY_DATE_DOC_AGG = "last_by_date";
    public static final String CLOUD_REGION_ID_FIELD = "cloud_region_id";
    public static final String CLOUD_REGION_PROVIDER_FIELD = "cloud_region_provider";
    public static final String RUN_ID_FIELD = "run_id";
    public static final String STORAGE_ID_FIELD = "storage_id";
    public static final String STORAGE_NAME_FIELD = "storage_name";
    public static final String STORAGE_CREATED_FIELD = "storage_created_date";
    public static final String PAGE = "page";
    public static final String TOTAL_PAGES = "totalPages";
    public static final String BILLING_DATE_FIELD = "created_date";
    public static final String HISTOGRAM_AGGREGATION_NAME = "hist_agg";
    public static final String ES_EMPTY_JSON = "{}";
    public static final String ES_MONTHLY_DATE_REGEXP = "%d-%02d-*";
    public static final String ES_WILDCARD = "*";
    public static final String ES_DOC_FIELDS_SEPARATOR = ".";
    public static final String ES_DOC_AGGS_SEPARATOR = ">";
    public static final String ES_ELEMENTS_SEPARATOR = ",";
    public static final String ES_FILTER_PATH = "filter_path";
    public static final String ES_INDICES_SEARCH_PATTERN = "/%s/_search";
    public static final String FIRST_LEVEL_AGG_PATTERN = "aggregations.*#%s";
    public static final String FIRST_LEVEL_TERMS_AGG_BUCKETS_PATTERN = FIRST_LEVEL_AGG_PATTERN + ".buckets";
    public static final String ES_TERMS_AGG_BUCKET_KEY = "key";
    public static final String BUCKET_DOCUMENTS = "bucketDocs";
    public static final String OWNER_FIELD = "owner";
    public static final String BILLING_CENTER_FIELD = "billing_center";
    public static final String CARDINALITY_AGG = "cardinality";
    public static final String PIPELINE_ID_FIELD = "pipeline";
    public static final String PIPELINE_NAME_FIELD = "pipeline_name";
    public static final String TOOL_FIELD = "tool";
    public static final String COMPUTE_TYPE_FIELD = "compute_type";
    public static final String INSTANCE_TYPE_FIELD = "instance_type";
    public static final String STARTED_FIELD = "started_date";
    public static final String FINISHED_FIELD = "finished_date";
    public static final String RUN = "run";
    public static final String STORAGE = "storage";
    public static final String RUNS = "runs";
    public static final String RUN_COST_AGG = "cost_runs";
    public static final String STORAGE_COST_AGG = "cost_storages";
    public static final String HISTOGRAM_AGGREGATION_FORMAT = "yyyy-MM";
    public static final String RUN_COUNT_AGG = "count_runs";
    public static final String PROVIDER_FIELD = "provider";
    public static final String SORT_AGG = "sort";
    public static final String DISCOUNT_SCRIPT_TEMPLATE = "_value + _value * (%s)";
    public static final String RESOURCE_TYPE = "resource_type";
    public static final String COMPUTE_GROUP = "COMPUTE";

    private BillingUtils() {
    }

    public static String asString(final Object value) {
        return value instanceof LocalDateTime ? asString((LocalDateTime) value)
                : value instanceof YearMonth ? asString((YearMonth) value)
                : value != null ? value.toString()
                : null;
    }

    public static String asString(final LocalDateTime value) {
        return Optional.ofNullable(value).map(EXPORT_DATE_TIME_FORMATTER::format).orElse(null);
    }

    public static String asString(final YearMonth value) {
        return value != null ? YEAR_MONTH_FORMATTER.format(value) : null;
    }

    public static LocalDateTime asDateTime(final Object value) {
        return asDateTime(asString(value));
    }

    public static LocalDateTime asDateTime(final String value) {
        return Optional.ofNullable(value)
                .map(it -> ELASTIC_DATE_TIME_FORMATTER.parse(it, LocalDateTime::from))
                .orElse(null);
    }

    public static String asDurationString(final Long value) {
        return asDividedString(value, DURATION_DIVISOR);
    }

    public static String asCostString(final Long value) {
        return asDividedString(value, COST_DIVISOR);
    }

    public static String asVolumeString(final Long value) {
        return asDividedString(value, VOLUME_DIVISOR);
    }

    public static String asPercentToDecimalString(final Long value) {
        return asDividedString(value, PERCENT_TO_DECIMAL_DIVISOR);
    }

    public static String asDividedString(final Long divider, final Long divisor) {
        return Optional.ofNullable(divider)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(divisor), DECIMAL_SCALE, RoundingMode.CEILING)
                .toString();
    }

    public static String getUserBillingCenter(final PipelineUser user,
                                              final String billingCenterKey,
                                              final MetadataManager metadataManager) {
        return Optional.ofNullable(metadataManager.loadMetadataItem(user.getId(), AclClass.PIPELINE_USER))
                .map(MetadataEntry::getData)
                .filter(MapUtils::isNotEmpty)
                .flatMap(attributes -> Optional.ofNullable(attributes.get(billingCenterKey)))
                .flatMap(value -> Optional.ofNullable(value.getValue()))
                .orElse(StringUtils.EMPTY);
    }

    public static boolean hasComputeResourceTypeFilter(final Map<String, List<String>> filters) {
        return filters.getOrDefault(RESOURCE_TYPE, Collections.emptyList()).contains(COMPUTE_GROUP);
    }

    private static long tenInPowerOf(final int scale) {
        return BigDecimal.ONE.setScale(scale, RoundingMode.CEILING)
                .unscaledValue()
                .longValue();
    }
}
