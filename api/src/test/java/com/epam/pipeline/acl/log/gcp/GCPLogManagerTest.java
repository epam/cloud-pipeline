package com.epam.pipeline.acl.log.gcp;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.region.CloudRegionDao;
import com.epam.pipeline.entity.log.*;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.entity.region.GCPRegion;
import com.epam.pipeline.exception.PipelineException;
import com.epam.pipeline.manager.cloud.gcp.GCPClient;
import com.epam.pipeline.manager.log.gcp.GCPLogManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.google.cloud.bigquery.*;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Severity;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.epam.pipeline.manager.log.LogManager.HOSTNAME;
import static com.epam.pipeline.manager.log.LogManager.MESSAGE;
import static com.epam.pipeline.manager.log.LogManager.MESSAGE_TIMESTAMP;
import static com.epam.pipeline.manager.log.LogManager.ID;
import static com.epam.pipeline.manager.log.LogManager.SERVICE_ACCOUNT;
import static com.epam.pipeline.manager.log.LogManager.SERVICE_NAME;
import static com.epam.pipeline.manager.log.LogManager.SEVERITY;
import static com.epam.pipeline.manager.log.LogManager.STORAGE_ID;
import static com.epam.pipeline.manager.log.LogManager.TYPE;
import static com.epam.pipeline.manager.log.LogManager.USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class GCPLogManagerTest {

    private static final String TABLE_NAME = "big_query_table_name";
    private static final Integer TIMEOUT_MS = 300_000;
    private static final int PAGE_SIZE = 2;
    private static final long MAX_BYTES_BILLED = 1_000_000_000L;
    public static final int AGGS_MAX_COUNT = 312;
    public static final int YEAR_2023 = 2023;
    public static final int DAY_OF_MONTH_1 = 1;
    public static final int DAY_OF_MONTH_30 = 30;
    public static final long EVENT_ID = 109L;
    public static final int MINUTE_29 = 29;
    public static final int MINUTE_59 = 59;
    public static final int HOUR_3 = 3;
    public static final int HOUR_23 = 23;
    public static final String USER_1 = "user1";
    public static final String USER_2 = "user2";
    public static final String HOST_1 = "host1";
    public static final String HOST_2 = "host2";
    public static final String SERVICE_1 = "service1";
    public static final String SERVICE_2 = "service2";
    public static final String TYPE_1 = "type1";
    public static final String TYPE_2 = "type2";
    public static final String SECURITY_LOG = "security_log";
    public static final String SINK_LABEL = "sink-label";
    public static final String SINK_LABEL_VALUE = "sink-label-value";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    public static final Long EVENT_ID1 = 1744351501175986176L;
    public static final Long EVENT_ID2 = 1744351501176002048L;
    public static final long STORAGE_ID1 = 37943L;
    public static final long STORAGE_ID2 = 52945L;
    public static final String INFO = "INFO";
    public static final String ERROR = "ERROR";

    @Mock
    private GCPClient gcpClient;

    @Mock
    private CloudRegionDao cloudRegionDao;

    @Mock
    private PreferenceManager preferenceManager;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private BigQuery bigQuery;

    @Mock
    private Logging logging;

    @Mock
    private TableResult filterTableResult;

    @Mock
    private TableResult countTableResult;

    @Mock
    private TableResult groupTableResult;

    @Mock
    private TableResult dictionaryTableResult;

    @Mock
    private AuthManager authManager;

    @InjectMocks
    private GCPLogManager gcpLogManager;

    private GCPRegion gcpRegion;

    @Before
    public void setUp() throws IOException {
        gcpRegion = new GCPRegion();
        gcpRegion.setProvider(CloudProvider.GCP);
        when(cloudRegionDao.loadDefaultRegion()).thenReturn(Optional.of(gcpRegion));
        when(gcpClient.buildBigQueryClient(eq(gcpRegion), anyInt(), anyInt())).thenReturn(bigQuery);
        when(gcpClient.buildCloudLoggingClient(eq(gcpRegion))).thenReturn(logging);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_BIG_QUERY_TABLE_NAME))
                .thenReturn(TABLE_NAME);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_READ_TIMEOUT_MILLS))
                .thenReturn(TIMEOUT_MS);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_CONNECT_TIMEOUT_MILLS))
                .thenReturn(TIMEOUT_MS);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_BIG_QUERY_MAX_BYTES))
                .thenReturn(MAX_BYTES_BILLED);
        when(preferenceManager.getPreference(SystemPreferences.SEARCH_LOGS_AGGS_MAX_COUNT)).thenReturn(AGGS_MAX_COUNT);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_LOG_NAME)).thenReturn(SECURITY_LOG);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_SINK_LABEL_KEY)).thenReturn(SINK_LABEL);
        when(preferenceManager.getPreference(SystemPreferences.GCP_LOGGING_SINK_LABEL_VALUE))
                .thenReturn(SINK_LABEL_VALUE);
        when(messageHelper.getMessage(anyString())).thenReturn("Error message");
        doNothing().when(logging).write(anyList(), anyObject());
    }

    @Test
    public void shouldFilterSuccess() throws InterruptedException {
        LogFilter logFilter = createFullLogFilter();
        setupFilterTableResult();
        setupCountTableResult(3L);
        when(bigQuery.query(any())).thenReturn(filterTableResult).thenReturn(countTableResult);

        ArgumentCaptor<QueryJobConfiguration> queryCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);

        LogPagination result = gcpLogManager.filter(logFilter);

        // Assert Basic Filter Behavior
        assertNotNull(result);
        assertThat(result.getPageSize()).isEqualTo(PAGE_SIZE);
        assertThat(result.getTotalHits()).isEqualTo(3L);
        assertThat(result.getLogEntries()).hasSize(2);
        //Assert PageMarker
        assertNotNull(result.getToken());
        assertThat(result.getToken().getId()).isEqualTo(3L);
        assertThat(result.getToken().getMessageTimestamp()).isEqualTo(
                LocalDateTime.parse("2023-01-03T12:00:00.000Z", DateTimeFormatter.ISO_DATE_TIME));

        verify(bigQuery, times(2)).query(queryCaptor.capture());

        // Assert Query Filter Conditions
        List<QueryJobConfiguration> capturedQueries = queryCaptor.getAllValues();
        String filterQuery = capturedQueries.get(0).getQuery();
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.service_account') = 'false'");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.user') IN ('user1','user2')");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.hostname') IN ('host1')");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.service_name') IN ('service1')");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.type') IN ('type1')");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.message') LIKE '%test%'");
        assertThat(filterQuery).contains("JSON_VALUE(json_payload,'$.message_timestamp') BETWEEN");
        assertThat(filterQuery).contains("CAST(JSON_VALUE(json_payload, '$.event_id') AS INT64) <= " + EVENT_ID);
        assertThat(filterQuery).contains(
                String.format("ORDER BY %s desc, %s desc LIMIT %s", MESSAGE_TIMESTAMP, ID, PAGE_SIZE + 1));
        assertThat(filterQuery).contains(TABLE_NAME.concat("._AllLogs"));


        String countQuery = capturedQueries.get(1).getQuery();
        assertThat(countQuery).contains("COUNT(1) as count");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.service_account') = 'false'");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.user') IN ('user1','user2')");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.hostname') IN ('host1')");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.service_name') IN ('service1')");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.type') IN ('type1')");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.message') LIKE '%test%'");
        assertThat(countQuery).contains("JSON_VALUE(json_payload,'$.message_timestamp') BETWEEN");
        assertThat(countQuery).contains("CAST(JSON_VALUE(json_payload, '$.event_id') AS INT64) <= " + EVENT_ID);
        assertThat(countQuery).contains(TABLE_NAME.concat("._AllLogs"));


        // Assert LogEntry Parsing
        List<LogEntry> logEntries = result.getLogEntries();

        // Check first LogEntry
        LogEntry entry1 = logEntries.get(0);
        assertThat(entry1.getEventId()).isEqualTo(1L);
        assertThat(entry1.getMessageTimestamp()).isEqualTo(
                LocalDateTime.parse("2023-01-01T12:00:00.000Z", DateTimeFormatter.ISO_DATE_TIME));
        assertThat(entry1.getUser()).isEqualTo(USER_1);
        assertThat(entry1.getSeverity()).isEqualTo(INFO);
        assertThat(entry1.getServiceName()).isEqualTo(SERVICE_1);
        assertThat(entry1.getType()).isEqualTo(TYPE_1);
        assertThat(entry1.getHostname()).isEqualTo(HOST_1);
        assertThat(entry1.getMessage()).isEqualTo("msg1");

        // Check second LogEntry
        LogEntry entry2 = logEntries.get(1);
        assertThat(entry2.getEventId()).isEqualTo(2L);
        assertThat(entry2.getMessageTimestamp()).isEqualTo(
                LocalDateTime.parse("2023-01-02T12:00:00.000Z", DateTimeFormatter.ISO_DATE_TIME));
        assertThat(entry2.getUser()).isEqualTo(USER_2);
        assertThat(entry2.getSeverity()).isEqualTo(ERROR);
        assertThat(entry2.getServiceName()).isEqualTo(SERVICE_2);
        assertThat(entry2.getType()).isEqualTo(TYPE_2);
        assertThat(entry2.getHostname()).isEqualTo(HOST_2);
        assertThat(entry2.getMessage()).isEqualTo("msg2");
    }

    @Test(expected = PipelineException.class)
    public void shouldFilterWithIOException() throws IOException {
        LogFilter logFilter = createLogFilter();
        when(gcpClient.buildBigQueryClient(any(), anyInt(), anyInt())).thenThrow(new IOException("BigQuery failure"));

        gcpLogManager.filter(logFilter);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFilterInvalidPagination() {
        LogFilter logFilter = new LogFilter();
        logFilter.setPagination(LogPaginationRequest.builder().pageSize(0).token(null).build());

        gcpLogManager.filter(logFilter);
    }

    @Test
    public void shouldGroupSuccess() throws InterruptedException {
        LogRequest logRequest = new LogRequest();
        logRequest.setGroupBy("user");
        setupGroupTableResult();
        when(bigQuery.query(any())).thenReturn(groupTableResult);

        ArgumentCaptor<QueryJobConfiguration> queryCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);

        Map<String, Long> result = gcpLogManager.group(logRequest);

        // Assert Basic Group Behavior
        assertNotNull(result);
        assertThat(result).hasSize(2);
        assertThat(result.get(USER_1)).isEqualTo(5L);
        assertThat(result.get(USER_2)).isEqualTo(3L);
        verify(bigQuery, times(1)).query(queryCaptor.capture());

        // Assert Query Conditions
        List<QueryJobConfiguration> capturedQueries = queryCaptor.getAllValues();
        String groupQuery = capturedQueries.get(0).getQuery();
        assertThat(groupQuery).contains("SELECT JSON_VALUE(json_payload, '$.user') AS user");
        assertThat(groupQuery).contains("COUNT(1) AS count");
        assertThat(groupQuery).contains("GROUP BY user");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldGroupWithEmptyGroupBy() {
        // Arrange
        LogRequest logRequest = new LogRequest();
        logRequest.setGroupBy("");

        gcpLogManager.group(logRequest);
    }

    @Test
    public void shouldGetFiltersSuccess() throws InterruptedException {
        setupDictionaryTableResult();
        when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(dictionaryTableResult);
        ArgumentCaptor<QueryJobConfiguration> queryCaptor = ArgumentCaptor.forClass(QueryJobConfiguration.class);

        LogFilter result = gcpLogManager.getFilters();

        assertNotNull(result);
        assertThat(result.getTypes()).isEqualTo(Arrays.asList(TYPE_1, TYPE_2, "type3"));
        assertThat(result.getServiceNames()).isEqualTo(Arrays.asList(SERVICE_1, SERVICE_2));
        assertThat(result.getHostnames()).isEqualTo(Arrays.asList(HOST_1, HOST_2, "host3", "host4"));

        verify(bigQuery, times(1)).query(queryCaptor.capture());

        // Assert Query Conditions
        List<QueryJobConfiguration> capturedQueries = queryCaptor.getAllValues();
        String filterQuery = capturedQueries.get(0).getQuery();
        assertThat(filterQuery).contains(
                "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.type') IGNORE NULLS) AS types");
        assertThat(filterQuery).contains(
                "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.service_name') IGNORE NULLS) AS service_names");
        assertThat(filterQuery).contains(
                "ARRAY_AGG(DISTINCT JSON_VALUE(json_payload, '$.hostname') IGNORE NULLS) AS hostnames");
        assertThat(filterQuery).contains(TABLE_NAME.concat("._AllLogs"));
    }

    @Test
    public void shouldGetFiltersWithNullValues() throws InterruptedException {
        setupEmptyDictionaryTableResult();
        when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(dictionaryTableResult);

        LogFilter result = gcpLogManager.getFilters();

        // Assert
        assertNotNull(result);
        assertTrue(result.getTypes().isEmpty());
        assertTrue(result.getServiceNames().isEmpty());
        assertTrue(result.getHostnames().isEmpty());
        verify(bigQuery, times(1)).query(any(QueryJobConfiguration.class));
    }

    @Test
    public void shouldReturnOnEmpty() {
        gcpLogManager.save(Collections.emptyList());

        verifyZeroInteractions(preferenceManager, cloudRegionDao, gcpClient);
    }

    @Test
    public void shouldSaveSuccess() throws IOException {
        when(authManager.isServiceUser(anyString())).thenReturn(true).thenReturn(false);

        final LogEntry logEntry1 = LogEntry.builder()
                .eventId(EVENT_ID1)
                .serviceName("pipe-mount1")
                .user(USER_1)
                .messageTimestamp(LocalDateTime.parse("2025-04-15 06:05:01.175", TIMESTAMP_FORMATTER))
                .storageId(STORAGE_ID1)
                .message("READ s3://e2e-mount-s3-1-11-04-2025-05-58-39/1KB")
                .hostname("ip-172-31-4-81")
                .type("audit")
                .severity(INFO)
                .build();

        final LogEntry logEntry2 = LogEntry.builder()
                .eventId(EVENT_ID2)
                .serviceName("pipe-mount2")
                .user(USER_2)
                .messageTimestamp(LocalDateTime.parse("2025-04-15 06:55:01.435", TIMESTAMP_FORMATTER))
                .storageId(STORAGE_ID2)
                .message("WRITE s3://e2e-mount-s3-1-11-04-2025-05-58-39/512MB")
                .hostname("ip-172-31-4-82")
                .type("log")
                .severity(ERROR)
                .build();

        gcpLogManager.save(Arrays.asList(logEntry1, logEntry2));

        //Assert
        ArgumentCaptor<Logging.WriteOption> optionArgCaptor = ArgumentCaptor.forClass(Logging.WriteOption.class);
        ArgumentCaptor<List<com.google.cloud.logging.LogEntry>> argCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(logging).write(argCaptor.capture(), optionArgCaptor.capture());

        List<com.google.cloud.logging.LogEntry> logEntries = argCaptor.getValue();
        assertThat(logEntries).hasSize(2);

        final com.google.cloud.logging.LogEntry gcpLogEntry1 = logEntries.get(0);
        assertEquals(Severity.INFO, gcpLogEntry1.getSeverity());
        assertEquals(Collections.singletonMap(SINK_LABEL, SINK_LABEL_VALUE), gcpLogEntry1.getLabels());

        Map<String, Value> payload1 = ((Struct) gcpLogEntry1.getPayload().getData()).getFieldsMap();
        assertThat(payload1.get(HOSTNAME).getStringValue()).isEqualTo("ip-172-31-4-81");
        assertThat(payload1.get(ID).getStringValue()).isEqualTo(EVENT_ID1.toString());
        assertThat(payload1.get(SERVICE_NAME).getStringValue()).isEqualTo("pipe-mount1");
        assertThat(payload1.get(MESSAGE).getStringValue())
                .isEqualTo("READ s3://e2e-mount-s3-1-11-04-2025-05-58-39/1KB");
        assertThat(payload1.get(TYPE).getStringValue()).isEqualTo("audit");
        assertThat(payload1.get(SEVERITY).getStringValue()).isEqualTo(INFO);
        assertThat(payload1.get(USER).getStringValue()).isEqualTo(USER_1);
        assertThat(payload1.get(MESSAGE_TIMESTAMP).getStringValue()).isEqualTo("2025-04-15T06:05:01.175+0000");
        assertThat(payload1.get(SERVICE_ACCOUNT).getBoolValue()).isEqualTo(true);
        assertThat(payload1.get(STORAGE_ID).getNumberValue()).isEqualTo(STORAGE_ID1);
        verify(authManager).isServiceUser(USER_1);

        final com.google.cloud.logging.LogEntry gcpLogEntry2 = logEntries.get(1);
        assertEquals(Severity.ERROR, gcpLogEntry2.getSeverity());
        assertEquals(Collections.singletonMap(SINK_LABEL, SINK_LABEL_VALUE), gcpLogEntry2.getLabels());

        Map<String, Value> payload2 = ((Struct) gcpLogEntry2.getPayload().getData()).getFieldsMap();
        assertThat(payload2.get(HOSTNAME).getStringValue()).isEqualTo("ip-172-31-4-82");
        assertThat(payload2.get(ID).getStringValue()).isEqualTo(EVENT_ID2.toString());
        assertThat(payload2.get(SERVICE_NAME).getStringValue()).isEqualTo("pipe-mount2");
        assertThat(payload2.get(MESSAGE).getStringValue())
                .isEqualTo("WRITE s3://e2e-mount-s3-1-11-04-2025-05-58-39/512MB");
        assertThat(payload2.get(TYPE).getStringValue()).isEqualTo("log");
        assertThat(payload2.get(SEVERITY).getStringValue()).isEqualTo(ERROR);
        assertThat(payload2.get(USER).getStringValue()).isEqualTo(USER_2);
        assertThat(payload2.get(MESSAGE_TIMESTAMP).getStringValue()).isEqualTo("2025-04-15T06:55:01.435+0000");
        assertThat(payload2.get(SERVICE_ACCOUNT).getBoolValue()).isEqualTo(false);
        assertThat(payload2.get(STORAGE_ID).getNumberValue()).isEqualTo(STORAGE_ID2);
        verify(authManager).isServiceUser(USER_2);

        Logging.WriteOption writeOption = optionArgCaptor.getValue();
        assertThat(writeOption).isEqualTo(Logging.WriteOption.logName(SECURITY_LOG));

        verify(cloudRegionDao).loadDefaultRegion();
        verify(gcpClient).buildCloudLoggingClient(gcpRegion);
        verify(preferenceManager).getPreference(SystemPreferences.GCP_LOGGING_LOG_NAME);
        verify(preferenceManager).getPreference(SystemPreferences.GCP_LOGGING_SINK_LABEL_KEY);
        verify(preferenceManager).getPreference(SystemPreferences.GCP_LOGGING_SINK_LABEL_VALUE);
    }

    private LogFilter createLogFilter() {
        LogFilter filter = new LogFilter();
        filter.setPagination(LogPaginationRequest.builder()
                .pageSize(PAGE_SIZE)
                .token(null)
                .build());
        filter.setSortOrder(SortOrder.DESC.toString());
        return filter;
    }

    private LogFilter createFullLogFilter() {
        LogFilter filter = new LogFilter();
        filter.setIncludeServiceAccountEvents(false);
        filter.setUsers(Arrays.asList(USER_1, USER_2));
        filter.setHostnames(Collections.singletonList(HOST_1));
        filter.setServiceNames(Collections.singletonList(SERVICE_1));
        filter.setTypes(Collections.singletonList(TYPE_1));
        filter.setMessage("test");
        filter.setMessageTimestampFrom(LocalDateTime.of(YEAR_2023, Month.MAY, DAY_OF_MONTH_1, HOUR_3, MINUTE_29));
        filter.setMessageTimestampTo(LocalDateTime.of(YEAR_2023, Month.APRIL, DAY_OF_MONTH_30, HOUR_23, MINUTE_59));
        filter.setPagination(LogPaginationRequest.builder()
                .pageSize(PAGE_SIZE)
                .token(new PageMarker(EVENT_ID, LocalDateTime.now()))
                .build());
        filter.setSortOrder(SortOrder.DESC.toString());
        return filter;
    }

    private void setupFilterTableResult() {
        List<FieldValueList> rows = new ArrayList<>();
        rows.add(createFieldValueList("1", "2023-01-01T12:00:00.000+0000", USER_1, INFO,
                SERVICE_1, TYPE_1, HOST_1, "msg1"));
        rows.add(createFieldValueList("2", "2023-01-02T12:00:00.000+0000", USER_2, ERROR,
                SERVICE_2, TYPE_2, HOST_2, "msg2"));
        rows.add(createFieldValueList("3", "2023-01-03T12:00:00.000+0000", "user3", "WARN",
                "service3", "type3", "host3", "msg3"));
        when(filterTableResult.iterateAll()).thenReturn(rows);
    }

    private void setupCountTableResult(long count) {
        FieldValueList row = mock(FieldValueList.class);
        FieldValue countValue = FieldValue.of(FieldValue.Attribute.PRIMITIVE, String.valueOf(count));
        when(row.get("count")).thenReturn(countValue);
        when(countTableResult.iterateAll()).thenReturn(Collections.singletonList(row));
    }

    private void setupGroupTableResult() {
        List<FieldValueList> rows = new ArrayList<>();
        rows.add(createGroupFieldValueList(USER_1, "5"));
        rows.add(createGroupFieldValueList(USER_2, "3"));
        when(groupTableResult.iterateAll()).thenReturn(rows);
    }

    private void setupDictionaryTableResult() {
        FieldValueList row = mock(FieldValueList.class);
        when(row.get("types")).thenReturn(FieldValue.of(FieldValue.Attribute.REPEATED,
                FieldValueList.of(Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, TYPE_1),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, TYPE_2),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "type3")))));
        when(row.get("service_names")).thenReturn(FieldValue.of(FieldValue.Attribute.REPEATED,
                FieldValueList.of(Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, SERVICE_1),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, SERVICE_2)))));
        when(row.get("hostnames")).thenReturn(FieldValue.of(FieldValue.Attribute.REPEATED,
                FieldValueList.of(Arrays.asList(
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, HOST_1),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, HOST_2),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "host3"),
                        FieldValue.of(FieldValue.Attribute.PRIMITIVE, "host4")))));
        when(dictionaryTableResult.iterateAll()).thenReturn(Collections.singletonList(row));
    }

    private void setupEmptyDictionaryTableResult() {
        FieldValueList row = mock(FieldValueList.class);
        when(row.get("types")).thenReturn(
                FieldValue.of(FieldValue.Attribute.REPEATED, FieldValueList.of(Collections.emptyList())));
        when(row.get("service_names")).thenReturn(
                FieldValue.of(FieldValue.Attribute.REPEATED, FieldValueList.of(Collections.emptyList())));
        when(row.get("hostnames")).thenReturn(
                FieldValue.of(FieldValue.Attribute.REPEATED, FieldValueList.of(Collections.emptyList())));
        when(dictionaryTableResult.iterateAll()).thenReturn(Collections.singletonList(row));
    }

    private FieldValueList createFieldValueList(String eventId, String timestamp, String user, String level,
                                                String serviceName, String type, String hostname, String message) {
        FieldValueList row = mock(FieldValueList.class);
        when(row.get("event_id")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, eventId));
        when(row.get("message_timestamp")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, timestamp));
        when(row.get("user")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, user));
        when(row.get("level")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, level));
        when(row.get("service_name")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, serviceName));
        when(row.get("type")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, type));
        when(row.get("hostname")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, hostname));
        when(row.get("message")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, message));
        return row;
    }

    private FieldValueList createGroupFieldValueList(String user, String count) {
        FieldValueList row = mock(FieldValueList.class);
        when(row.get("user")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, user));
        when(row.get("count")).thenReturn(FieldValue.of(FieldValue.Attribute.PRIMITIVE, count));
        return row;
    }
}