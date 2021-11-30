package com.epam.pipeline.manager.billing;

import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.Charsets;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.stream.Stream;

@RequiredArgsConstructor
public abstract class AbstractBillingWriterTest<B> {

    public static final Long ONE = 1L;
    public static final Long TEN = 10L;
    public static final long ONE_DOLLAR = 10_000L;
    public static final long TEN_DOLLARS = 10L * ONE_DOLLAR;
    public static final long ONE_HOUR = 60L;
    public static final long TEN_HOURS = 10L * ONE_HOUR;
    public static final long ONE_GB = 1_000_000_000L;
    public static final long TEN_GBS = 10L * ONE_GB;

    public static final long ID = CommonCreatorConstants.ID;
    public static final long ID_2 = CommonCreatorConstants.ID_2;
    public static final DataStorageType TYPE = DataStorageType.S3;
    public static final String MISSING = "unknown";
    public static final String OWNER = "some_owner";
    public static final String OTHER_OWNER = "some_other_owner";
    public static final String BILLING_CENTER = "some_billing_center";
    public static final String PIPELINE = "some_pipeline";
    public static final String TOOL = "some_registry/some_group/some_tool:some_tag";
    public static final String INSTANCE_TYPE = "some_instance_type";
    public static final String STORAGE = "some_storage";
    public static final String REGION = "some_region";
    public static final String PROVIDER = "some_provider";
    public static final String GRAND_TOTAL = "Grand total";
    public static final LocalDateTime START_DATE_TIME = LocalDateTime.of(2021, 10, 11, 11, 11, 11, 111_000_000);
    public static final LocalDateTime FINISH_DATE_TIME = LocalDateTime.of(2021, 11, 11, 11, 11, 11, 111_000_000);
    public static final LocalDate START_DATE = START_DATE_TIME.toLocalDate();
    public static final LocalDate FINISH_DATE = FINISH_DATE_TIME.toLocalDate();
    public static final YearMonth START_YEAR_MONTH = YearMonth.from(START_DATE);
    public static final YearMonth FINISH_YEAR_MONTH = YearMonth.from(FINISH_DATE);

    private final String expected;

    @Test
    public void writerShouldGenerateReport() throws IOException {
        assertEquals(expected, getReportBytes());
    }

    private byte[] getReportBytes() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream)) {
            final BillingWriter<B> writer = getWriter(outputStreamWriter);
            writer.writeHeader();
            billings().forEach(writer::write);
            writer.flush();
            return byteArrayOutputStream.toByteArray();
        }
    }

    private void assertEquals(final String expectedResource, final byte[] actualBytes) throws IOException {
        try (ByteArrayInputStream actualByteArrayInputStream = new ByteArrayInputStream(actualBytes);
             InputStreamReader actualInputStreamReader = new InputStreamReader(actualByteArrayInputStream);
             BufferedReader actualBufferedReader = new BufferedReader(actualInputStreamReader);
             InputStream expectedInputStream = getClass().getResourceAsStream(expectedResource);
             InputStreamReader expectedInputStreamReader = new InputStreamReader(expectedInputStream, Charsets.UTF_8);
             BufferedReader expectedBufferedReader = new BufferedReader(expectedInputStreamReader)) {
            while (true) {
                final String expectedLine = expectedBufferedReader.readLine();
                final String actualLine = actualBufferedReader.readLine();
                Assert.assertEquals(expectedLine, actualLine);
                if (expectedLine == null) {
                    break;
                }
            }
        }
    }

    public abstract BillingWriter<B> getWriter(Writer writer);

    public abstract Stream<B> billings();
}
