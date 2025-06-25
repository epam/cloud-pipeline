package com.epam.pipeline.manager.log.gcp;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.log.LogEntry;
import com.epam.pipeline.entity.log.LogFilter;
import com.epam.pipeline.entity.log.LogPagination;
import com.epam.pipeline.entity.log.LogPaginationRequest;
import com.epam.pipeline.entity.log.LogRequest;
import com.epam.pipeline.entity.log.PageMarker;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.ObjectNotFoundException;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.log.LogManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.pipeline.entity.region.CloudProvider.GCP;

/**
 * Implementation of {@link com.epam.pipeline.manager.log.LogManager} that retrieves and manages logs from GCP BigQuery.
 * This manager is activated when the 'logging.provider' property is set to 'gcp'.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "logging.provider", havingValue = "gcp")
public class GCPLogManager implements LogManager {
    private static final String TYPES_ALIAS = "types";
    private static final String SERVICE_NAMES_ALIAS = "service_names";
    private static final String HOSTNAMES_ALIAS = "hostnames";
    private static final String JSON_PAYLOAD = "json_payload";
    private static final String COUNT_COLUMN = "COUNT(1) as count";
    private static final DateTimeFormatter LOCAL_DATE_TO_BIGQUERY_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter BIG_QUERY_TO_LOCAL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final String SQL_SELECT_TEMPLATE = "SELECT %s FROM `%s._AllLogs` WHERE json_payload IS NOT NULL ";
    private static final String SQL_POSTFIX_TEMPLATE = "%s ORDER BY %s %s, %s %s LIMIT %s";
    private static final String DICTIONARY_QUERY_COLUMNS = String.join(", ",
            "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.type') IGNORE NULLS) AS " + TYPES_ALIAS,
            "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.service_name') IGNORE NULLS) AS " + SERVICE_NAMES_ALIAS,
            "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.hostname') IGNORE NULLS) AS " + HOSTNAMES_ALIAS);
    private static final String FILTER_QUERY_COLUMNS = String.join(", ",
            "JSON_VALUE(json_payload, '$.service_account') AS service_account",
            "JSON_VALUE(json_payload, '$.level') AS level",
            "JSON_VALUE(json_payload, '$.service_name') AS service_name",
            "JSON_VALUE(json_payload, '$.type') AS type",
            "JSON_VALUE(json_payload, '$.hostname') AS hostname",
            "JSON_VALUE(json_payload, '$.message') AS message",
            "JSON_VALUE(json_payload, '$.user') AS user",
            "JSON_VALUE(json_payload, '$.message_timestamp') AS message_timestamp",
            "JSON_VALUE(json_payload, '$.event_id') AS event_id");
    private static final String GROUP_QUERY_TEMPLATE =
            "SELECT JSON_VALUE(%s, '$.%s') AS %s, COUNT(1) AS count " +
                    "FROM `%s._AllLogs` " +
                    "WHERE json_payload IS NOT NULL %s " +
                    "GROUP BY %s " +
                    "ORDER BY count DESC " +
                    "LIMIT %d";

    private final GCPClient gcpClient;
    private final CloudRegionDao cloudRegionDao;
    private final PreferenceManager preferenceManager;
    private final MessageHelper messageHelper;

    /**
     * Retrieves available filter values for the log entries.
     * @return A LogFilter object containing available filter values
     */
    @Override
    public LogFilter getFilters() {
        try {
            final BigQuery bigQueryClient = getBigQueryClient();
            final String query = String.format(SQL_SELECT_TEMPLATE, DICTIONARY_QUERY_COLUMNS, getTableName());
            final TableResult resultSet = executeQueryWithTimeout(bigQueryClient, query);
            final LogFilter logFilter = new LogFilter();

            resultSet.iterateAll().forEach(row -> {
                logFilter.setTypes(extractStringValues(row, TYPES_ALIAS));
                logFilter.setServiceNames(extractStringValues(row, SERVICE_NAMES_ALIAS));
                logFilter.setHostnames(extractStringValues(row, HOSTNAMES_ALIAS));
            });

            return logFilter;
        } catch (IOException e) {
            log.error("Failed to initialize BigQuery client for logs filtering", e);
            throw new PipelineException(e);
        }
    }

    /**
     * Retrieves log entries based on the provided filter criteria.
     * @param logFilter The filter criteria to apply
     * @return {@link LogPagination} object with related search result and additional information
     */
    @Override
    public LogPagination filter(LogFilter logFilter) {
        validateFilterPagination(logFilter);
        final int pageSize = logFilter.getPagination().getPageSize();

        try {
            final BigQuery bigQueryClient = getBigQueryClient();

            final String query = buildFullQuery(logFilter);
            final List<LogEntry> logEntries = executeLogQuery(bigQueryClient, query);

            final String countQuery = buildCountQuery(logFilter);
            final long totalCount = executeTotalHitsQuery(bigQueryClient, countQuery);

            return LogPagination.builder()
                    .logEntries(logEntries.stream().limit(pageSize).collect(Collectors.toList()))
                    .token(getToken(logEntries, pageSize))
                    .pageSize(pageSize)
                    .totalHits(totalCount)
                    .build();
        } catch (IOException e) {
            log.error("Failed to initialize BigQuery client for logs filtering", e);
            throw new PipelineException(e);
        }
    }

    /**
     * Groups log entries by a specified field and counts occurrences.
     * @param logRequest The request containing grouping criteria
     * @return Map of field values to their counts
     */
    @Override
    public Map<String, Long> group(final LogRequest logRequest) {
        try {
            final BigQuery bigQueryClient = getBigQueryClient();
            final String query = buildGroupQuery(logRequest);

            final TableResult resultSet = executeQueryWithTimeout(bigQueryClient, query);

            final Map<String, Long> result = new LinkedHashMap<>();
            resultSet.iterateAll().forEach(row -> {
                final String groupBy = logRequest.getGroupBy();
                if (!row.get(groupBy).isNull()) {
                    result.put(row.get(groupBy).getStringValue(), row.get("count").getLongValue());
                }
            });

            return result;
        } catch (IOException e) {
            log.error("Failed to initialize BigQuery client for logs filtering", e);
            throw new PipelineException(e);
        }
    }

    @Override
    public void save(List<LogEntry> logEntries) {
        throw new UnsupportedOperationException("Not supported currently.");
    }

    private String constructQueryFilter(final LogFilter logFilter) {
        if (logFilter == null) {
            return "";
        }

        final StringBuilder whereClause = new StringBuilder();

        // Filter by service account events
        if (BooleanUtils.isNotTrue(logFilter.getIncludeServiceAccountEvents())) {
            appendCondition(whereClause, String.format("JSON_VALUE(%s,'$.%s') = 'false'",
                    JSON_PAYLOAD, SERVICE_ACCOUNT));
        }

        // Apply user filters (case-insensitive)
        if (CollectionUtils.isNotEmpty(logFilter.getUsers())) {
            final List<String> users = logFilter.getUsers().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(user -> user.replace("'", "''"))
                    .collect(Collectors.toList());

            if (!users.isEmpty()) {
                appendInClause(whereClause, USER, users);
            }
        }

        // Apply hostname filters
        if (CollectionUtils.isNotEmpty(logFilter.getHostnames())) {
            List<String> hostnames = logFilter.getHostnames().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(hostname -> hostname.replace("'", "''"))
                    .collect(Collectors.toList());

            if (!hostnames.isEmpty()) {
                appendInClause(whereClause, HOSTNAME, hostnames);
            }
        }

        // Apply service name filters
        if (CollectionUtils.isNotEmpty(logFilter.getServiceNames())) {
            List<String> serviceNames = logFilter.getServiceNames().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(name -> name.replace("'", "''"))
                    .collect(Collectors.toList());

            if (!serviceNames.isEmpty()) {
                appendInClause(whereClause, SERVICE_NAME, serviceNames);
            }
        }

        // Apply type filters
        if (CollectionUtils.isNotEmpty(logFilter.getTypes())) {
            List<String> types = logFilter.getTypes().stream()
                    .filter(StringUtils::isNotBlank)
                    .map(type -> type.replace("'", "''"))
                    .collect(Collectors.toList());

            if (!types.isEmpty()) {
                appendInClause(whereClause, TYPE, types);
            }
        }

        // Filter by message text (partial match)
        if (StringUtils.isNotEmpty(logFilter.getMessage())) {
            String escapedMessage = logFilter.getMessage().replace("'", "''");
            appendCondition(whereClause, String.format("JSON_VALUE(%s,'$.%s') LIKE '%%%s%%'",
                    JSON_PAYLOAD, MESSAGE, escapedMessage));
        }

        // Filter by message timestamp range
        if (logFilter.getMessageTimestampFrom() != null || logFilter.getMessageTimestampTo() != null) {
            appendTimestampRange(whereClause, logFilter.getMessageTimestampFrom(),
                    logFilter.getMessageTimestampTo());
        }

        // Apply pagination token filtering if present
        applyOrderBasedPagination(whereClause, logFilter);

        return whereClause.length() > 0 ? whereClause.toString() : "";
    }

    private List<LogEntry> executeLogQuery(final BigQuery bigQueryClient, final String query) {
        final TableResult resultSet = executeQueryWithTimeout(bigQueryClient, query);
        List<LogEntry> logEntries = new ArrayList<>();

        resultSet.iterateAll().forEach(row -> {
            LogEntry logEntry = LogEntry.builder()
                    .severity(row.get(SEVERITY).isNull() ? DEFAULT_SEVERITY : row.get(SEVERITY).getStringValue())
                    .messageTimestamp(parseTimestamp(row.get(MESSAGE_TIMESTAMP)))
                    .serviceName(extractStringValue(row, SERVICE_NAME))
                    .hostname(extractStringValue(row, HOSTNAME))
                    .message(extractStringValue(row, MESSAGE))
                    .eventId(extractNumericValue(row, ID))
                    .type(extractStringValue(row, TYPE))
                    .user(extractStringValue(row, USER))
                    .build();
            logEntries.add(logEntry);
        });

        return logEntries;
    }

    private TableResult executeQueryWithTimeout(final BigQuery bigQueryClient, final String query) {
        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query)
                .setMaximumBytesBilled(
                        preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_BIG_QUERY_MAX_BYTES))
                .build();

        log.debug("GCP BigQuery logs request: {}", queryConfig);
        try {
            final TableResult resultSet = bigQueryClient.query(queryConfig);
            return resultSet;
        } catch (InterruptedException e) {
            log.error("BigQuery grouping operation was interrupted", e);
            throw new PipelineException(e);
        }
    }

    private long executeTotalHitsQuery(final BigQuery bigQueryClient, final String countQuery) {
        final TableResult countResultSet = executeQueryWithTimeout(bigQueryClient, countQuery);
        final BigDecimal count = countResultSet.iterateAll().iterator().next().get("count").getNumericValue();
        return count.longValue();
    }

    private String extractStringValue(final FieldValueList row, final String fieldName) {
        return row.get(fieldName).isNull() ? null : row.get(fieldName).getStringValue();
    }

    private Long extractNumericValue(final FieldValueList row, final String fieldName) {
        return row.get(fieldName).isNull() ? null : row.get(fieldName).getNumericValue().longValue();
    }

    private List<String> extractStringValues(final FieldValueList row, final String columnName) {
        if (row.get(columnName).isNull()) {
            return Collections.emptyList();
        }

        return ((FieldValueList) row.get(columnName).getValue())
                .stream()
                .map(FieldValue::getStringValue)
                .collect(Collectors.toList());
    }

    private LocalDateTime parseTimestamp(final FieldValue fieldValue) {
        if (fieldValue.isNull()) {
            return null;
        }
        String timestampValue = fieldValue.getStringValue();

        return timestampValue != null ? LocalDateTime.parse(timestampValue, BIG_QUERY_TO_LOCAL_DATE_FORMATTER) : null;
    }

    private void applyOrderBasedPagination(final StringBuilder whereClause, final LogFilter logFilter) {
        final SortOrder sortOrder = getSortOrder(logFilter);

        final PageMarker pageMarker = Optional.ofNullable(logFilter.getPagination())
                .map(LogPaginationRequest::getToken)
                .orElse(null);

        if (pageMarker != null) {
            Assert.isTrue(pageMarker.getId() != null && pageMarker.getMessageTimestamp() != null,
                    "Token should contain id and messageTimestamp values");

            if (sortOrder == SortOrder.DESC) {
                appendCondition(whereClause, String.format("CAST(JSON_VALUE(%s, '$.%s') AS INT64) <= %s",
                        JSON_PAYLOAD, ID, pageMarker.getId()));
                appendTimestampRange(whereClause, null, pageMarker.getMessageTimestamp());
            } else {
                appendCondition(whereClause, String.format("CAST(JSON_VALUE(%s, '$.%s') AS INT64) >= %s",
                        JSON_PAYLOAD, ID, pageMarker.getId()));
                appendTimestampRange(whereClause, pageMarker.getMessageTimestamp(), null);
            }
        }
    }

    private void appendCondition(final StringBuilder whereClause, final String condition) {
        whereClause.append(" AND ");
        whereClause.append(condition);
    }

    private void appendInClause(final StringBuilder whereClause, final String field, final List<String> values) {
        if (values.isEmpty()) {
            return;
        }

        String inClause = String.format("JSON_VALUE(%s,'$.%s') IN ('%s')",
                JSON_PAYLOAD, field, String.join("','", values));

        appendCondition(whereClause, inClause);
    }

    private void appendTimestampRange(final StringBuilder whereClause,
                                      final LocalDateTime timestampFrom,
                                      final LocalDateTime timestampTo) {
        if (timestampFrom == null && timestampTo == null) {
            return;
        }

        final String timestampField = String.format("JSON_VALUE(%s,'$.%s')", JSON_PAYLOAD, MESSAGE_TIMESTAMP);

        if (timestampFrom != null && timestampTo != null) {
            appendCondition(whereClause,
                    String.format("%s BETWEEN '%s+0000' AND '%s+0000'",
                            timestampField,
                            LOCAL_DATE_TO_BIGQUERY_FORMATTER.format(timestampFrom),
                            LOCAL_DATE_TO_BIGQUERY_FORMATTER.format(timestampTo)));
        } else if (timestampFrom != null) {
            appendCondition(whereClause, String.format("%s >= '%s+0000'", timestampField,
                    LOCAL_DATE_TO_BIGQUERY_FORMATTER.format(timestampFrom)));
        } else {
            appendCondition(whereClause, String.format("%s <= '%s+0000'", timestampField,
                    LOCAL_DATE_TO_BIGQUERY_FORMATTER.format(timestampTo)));
        }
    }

    private String buildFullQuery(final LogFilter logFilter) {
        final SortOrder sortOrder = getSortOrder(logFilter);
        final String whereClause = constructQueryFilter(logFilter);

        return String.format(
                SQL_SELECT_TEMPLATE + SQL_POSTFIX_TEMPLATE,
                FILTER_QUERY_COLUMNS,
                getTableName(),
                whereClause.isEmpty() ? "" : whereClause,
                MESSAGE_TIMESTAMP,
                sortOrder,
                ID,
                sortOrder,
                logFilter.getPagination().getPageSize() + 1
        );
    }

    private String buildCountQuery(final LogFilter logFilter) {
        final String whereClause = constructQueryFilter(logFilter);
        return String.format(SQL_SELECT_TEMPLATE, COUNT_COLUMN, getTableName()).concat(whereClause);
    }

    private String buildGroupQuery(final LogRequest logRequest) {
        final String groupBy = logRequest.getGroupBy();
        Assert.isTrue(StringUtils.isNotBlank(groupBy), "Group by field not provided.");

        final String whereClause = constructQueryFilter(logRequest.getFilter());
        return String.format(
                GROUP_QUERY_TEMPLATE,
                JSON_PAYLOAD,
                groupBy,
                groupBy,
                getTableName(),
                whereClause.isEmpty() ? "" : whereClause,
                groupBy,
                preferenceManager.getPreference(SystemPreferences.SEARCH_LOGS_AGGS_MAX_COUNT)
        );
    }

    private PageMarker getToken(final List<LogEntry> items, final int pageSize) {
        if (items == null || items.size() <= pageSize) {
            return null;
        }
        final LogEntry entry = items.get(pageSize);
        return new PageMarker(entry.getEventId(), entry.getMessageTimestamp());
    }

    private SortOrder getSortOrder(final LogFilter logFilter) {
        return logFilter.getSortOrder() != null
                ? SortOrder.fromString(logFilter.getSortOrder())
                : SortOrder.DESC;
    }

    private void validateFilterPagination(final LogFilter logFilter) {
        final LogPaginationRequest pagination = logFilter.getPagination();
        Assert.notNull(pagination, messageHelper.getMessage(MessageConstants.ERROR_PAGINATION_IS_NOT_PROVIDED));
        Assert.isTrue(pagination.getPageSize() > 0,
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_PAGE_INDEX_OR_SIZE));
    }

    private BigQuery getBigQueryClient() throws IOException {
        final GCPRegion gcpRegion = (GCPRegion) cloudRegionDao.loadDefaultRegion()
                .filter(r -> GCP == r.getProvider())
                .orElseThrow(() -> new ObjectNotFoundException("No Default Region for GCP"));

        return gcpClient.buildBigQueryClient(gcpRegion,
                preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_READ_TIMEOUT_MILLS),
                preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_CONNECT_TIMEOUT_MILLS)
                );
    }

    private String getTableName() {
        return preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_BIG_QUERY_TABLE_NAME);
    }
}