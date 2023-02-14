package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"checkstyle:MagicNumber", "PMD.AvoidDuplicateLiterals"})
class AwsStoragePriceListLoaderTest {

    public static final String US_EAST_1_REGION = "us-east-1";
    public static final String S3_PRICE_LIST_JSON = "{\n" +
            "  \"formatVersion\" : \"v1.0\",\n" +
            "  \"disclaimer\" : \"This pricing list is for informational purposes only. All prices are subject to " +
            "                     the additional terms included in the pricing pages on http://aws.amazon.com.\",\n" +
            "  \"offerCode\" : \"AmazonS3\",\n" +
            "  \"version\" : \"20230123170643\",\n" +
            "  \"publicationDate\" : \"2023-01-23T17:06:43Z\",\n" +
            "  \"products\" : {\n" +
            "    \"WP9ANXZGBYYSGJEA\" : {\n" +
            "      \"sku\" : \"WP9ANXZGBYYSGJEA\",\n" +
            "      \"productFamily\" : \"Storage\",\n" +
            "      \"attributes\" : {\n" +
            "        \"servicecode\" : \"AmazonS3\",\n" +
            "        \"location\" : \"US East (N. Virginia)\",\n" +
            "        \"locationType\" : \"AWS Region\",\n" +
            "        \"availability\" : \"99.99%\",\n" +
            "        \"storageClass\" : \"General Purpose\",\n" +
            "        \"volumeType\" : \"Standard\",\n" +
            "        \"usagetype\" : \"TimedStorage-ByteHrs\",\n" +
            "        \"operation\" : \"\",\n" +
            "        \"durability\" : \"99.999999999%\",\n" +
            "        \"regionCode\" : \"" + US_EAST_1_REGION + "\",\n" +
            "        \"servicename\" : \"Amazon Simple Storage Service\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"VD5B635CNWMMUVCM\" : {\n" +
            "      \"sku\" : \"VD5B635CNWMMUVCM\",\n" +
            "      \"productFamily\" : \"Storage\",\n" +
            "      \"attributes\" : {\n" +
            "        \"servicecode\" : \"AmazonS3\",\n" +
            "        \"location\" : \"US East (N. Virginia)\",\n" +
            "        \"locationType\" : \"AWS Region\",\n" +
            "        \"availability\" : \"99.99%\",\n" +
            "        \"storageClass\" : \"Archive\",\n" +
            "        \"volumeType\" : \"Glacier Deep Archive\",\n" +
            "        \"usagetype\" : \"APN2-TimedStorage-GDA-Staging\",\n" +
            "        \"operation\" : \"\",\n" +
            "        \"durability\" : \"99.999999999%\",\n" +
            "        \"regionCode\" : \""+ US_EAST_1_REGION + "\",\n" +
            "        \"servicename\" : \"Amazon Simple Storage Service\"\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"terms\": {\n" +
            "    \"OnDemand\": {\n" +
            "      \"VD5B635CNWMMUVCM\" : {\n" +
            "        \"VD5B635CNWMMUVCM.JRTCKXETXF\" : {\n" +
            "          \"offerTermCode\" : \"JRTCKXETXF\",\n" +
            "          \"sku\" : \"VD5B635CNWMMUVCM\",\n" +
            "          \"effectiveDate\" : \"2023-01-01T00:00:00Z\",\n" +
            "          \"priceDimensions\" : {\n" +
            "            \"VD5B635CNWMMUVCM.JRTCKXETXF.6YS6EN2CT7\" : {\n" +
            "              \"rateCode\" : \"VD5B635CNWMMUVCM.JRTCKXETXF.6YS6EN2CT7\",\n" +
            "              \"description\" : \"$0.023 per GB-Month of storage used in GlacierStagingStorage\",\n" +
            "              \"beginRange\" : \"0\",\n" +
            "              \"endRange\" : \"Inf\",\n" +
            "              \"unit\" : \"GB-Mo\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0230000000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            }\n" +
            "          },\n" +
            "          \"termAttributes\" : { }\n" +
            "        }\n" +
            "      },\n" +
            "      \"WP9ANXZGBYYSGJEA\" : {\n" +
            "        \"WP9ANXZGBYYSGJEA.JRTCKXETXF\" : {\n" +
            "          \"offerTermCode\" : \"JRTCKXETXF\",\n" +
            "          \"sku\" : \"WP9ANXZGBYYSGJEA\",\n" +
            "          \"effectiveDate\" : \"2023-01-01T00:00:00Z\",\n" +
            "          \"priceDimensions\" : {\n" +
            "            \"WP9ANXZGBYYSGJEA.JRTCKXETXF.PGHJ3S3EYE\" : {\n" +
            "              \"rateCode\" : \"WP9ANXZGBYYSGJEA.JRTCKXETXF.PGHJ3S3EYE\",\n" +
            "              \"description\" : \"$0.023 per GB - first 50 TB / month of storage used\",\n" +
            "              \"beginRange\" : \"0\",\n" +
            "              \"endRange\" : \"51200\",\n" +
            "              \"unit\" : \"GB-Mo\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0230000000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            },\n" +
            "            \"WP9ANXZGBYYSGJEA.JRTCKXETXF.D42MF2PVJS\" : {\n" +
            "              \"rateCode\" : \"WP9ANXZGBYYSGJEA.JRTCKXETXF.D42MF2PVJS\",\n" +
            "              \"description\" : \"$0.022 per GB - next 450 TB / month of storage used\",\n" +
            "              \"beginRange\" : \"51200\",\n" +
            "              \"endRange\" : \"512000\",\n" +
            "              \"unit\" : \"GB-Mo\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0220000000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            },\n" +
            "            \"WP9ANXZGBYYSGJEA.JRTCKXETXF.PXJDJ3YRG3\" : {\n" +
            "              \"rateCode\" : \"WP9ANXZGBYYSGJEA.JRTCKXETXF.PXJDJ3YRG3\",\n" +
            "              \"description\" : \"$0.021 per GB - storage used / month over 500 TB\",\n" +
            "              \"beginRange\" : \"512000\",\n" +
            "              \"endRange\" : \"Inf\",\n" +
            "              \"unit\" : \"GB-Mo\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0210000000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            }\n" +
            "          },\n" +
            "          \"termAttributes\" : { }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    private static final String DEEP_ARCHIVE_PRICE_LIST = "{\n" +
            "  \"formatVersion\" : \"v1.0\",\n" +
            "  \"disclaimer\" : \"This pricing list is for informational purposes only.\",\n" +
            "  \"offerCode\" : \"AmazonS3GlacierDeepArchive\",\n" +
            "  \"version\" : \"20230207001700\",\n" +
            "  \"publicationDate\" : \"2023-02-07T00:17:00Z\",\n" +
            "  \"products\" : {\n" +
            "    \"SKAKWE8C8SPXCVHA\" : {\n" +
            "      \"sku\" : \"SKAKWE8C8SPXCVHA\",\n" +
            "      \"attributes\" : {\n" +
            "        \"servicecode\" : \"AmazonS3GlacierDeepArchive\",\n" +
            "        \"location\" : \"US East (N. Virginia)\",\n" +
            "        \"locationType\" : \"AWS Region\",\n" +
            "        \"storageClass\" : \"Archive\",\n" +
            "        \"volumeType\" : \"Glacier Deep Archive\",\n" +
            "        \"usagetype\" : \"TimedStorage-GDA-ByteHrs\",\n" +
            "        \"operation\" : \"\",\n" +
            "        \"regionCode\" : \"us-east-1\",\n" +
            "        \"servicename\" : \"Amazon S3 Glacier Deep Archive\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"EQ2HJHQR2HBHM5U8\" : {\n" +
            "      \"sku\" : \"EQ2HJHQR2HBHM5U8\",\n" +
            "      \"attributes\" : {\n" +
            "        \"servicecode\" : \"AmazonS3GlacierDeepArchive\",\n" +
            "        \"location\" : \"US East (N. Virginia)\",\n" +
            "        \"locationType\" : \"AWS Region\",\n" +
            "        \"group\" : \"S3-API-GDA-Tier1\",\n" +
            "        \"groupDescription\" : \"Initiate Multipart Upload to Glacier Deep Archive\",\n" +
            "        \"usagetype\" : \"Requests-GDA-Tier1\",\n" +
            "        \"operation\" : \"InitiateMultipartUpload\",\n" +
            "        \"regionCode\" : \"us-east-1\",\n" +
            "        \"servicename\" : \"Amazon S3 Glacier Deep Archive\"\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"terms\" : {\n" +
            "    \"OnDemand\" : {\n" +
            "      \"EQ2HJHQR2HBHM5U8\" : {\n" +
            "        \"EQ2HJHQR2HBHM5U8.JRTCKXETXF\" : {\n" +
            "          \"offerTermCode\" : \"JRTCKXETXF\",\n" +
            "          \"sku\" : \"EQ2HJHQR2HBHM5U8\",\n" +
            "          \"effectiveDate\" : \"2023-02-01T00:00:00Z\",\n" +
            "          \"priceDimensions\" : {\n" +
            "            \"EQ2HJHQR2HBHM5U8.JRTCKXETXF.6YS6EN2CT7\" : {\n" +
            "              \"rateCode\" : \"EQ2HJHQR2HBHM5U8.JRTCKXETXF.6YS6EN2CT7\",\n" +
            "              \"description\" : \"$0.005 per 1,000 InitiateMultipartUpload " +
            "                                  requests in US East (N. Virginia)\",\n" +
            "              \"beginRange\" : \"0\",\n" +
            "              \"endRange\" : \"Inf\",\n" +
            "              \"unit\" : \"Requests\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0000050000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            }\n" +
            "          },\n" +
            "          \"termAttributes\" : { }\n" +
            "        }\n" +
            "      },\n" +
            "      \"SKAKWE8C8SPXCVHA\" : {\n" +
            "        \"SKAKWE8C8SPXCVHA.JRTCKXETXF\" : {\n" +
            "          \"offerTermCode\" : \"JRTCKXETXF\",\n" +
            "          \"sku\" : \"SKAKWE8C8SPXCVHA\",\n" +
            "          \"effectiveDate\" : \"2023-02-01T00:00:00Z\",\n" +
            "          \"priceDimensions\" : {\n" +
            "            \"SKAKWE8C8SPXCVHA.JRTCKXETXF.6YS6EN2CT7\" : {\n" +
            "              \"rateCode\" : \"SKAKWE8C8SPXCVHA.JRTCKXETXF.6YS6EN2CT7\",\n" +
            "              \"description\" : \"$0.00099 per GB-Month for storage used in Glacier " +
            "                                  Deep Archive in US East (N. Virginia)\",\n" +
            "              \"beginRange\" : \"0\",\n" +
            "              \"endRange\" : \"Inf\",\n" +
            "              \"unit\" : \"GB-Mo\",\n" +
            "              \"pricePerUnit\" : {\n" +
            "                \"USD\" : \"0.0009900000\"\n" +
            "              },\n" +
            "              \"appliesTo\" : [ ]\n" +
            "            }\n" +
            "          },\n" +
            "          \"termAttributes\" : { }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public static final String STANDARD_SC = "STANDARD";
    public static final String DEEP_ARCHIVE_SC = "DEEP_ARCHIVE";
    private final AwsStoragePriceListLoader s3listLoader = Mockito.spy(
            new AwsStoragePriceListLoader("AmazonS3",
                    PriceLoadingMode.JSON, "https://localhost/mock.json")
    );

    private final AwsStoragePriceListLoader s3DeepArchivelistLoader = Mockito.spy(
            new AwsStoragePriceListLoader("AmazonS3GlacierDeepArchive",
                    PriceLoadingMode.JSON, "https://localhost/mock.json")
    );

    private final StoragePriceListLoader composeListLoader = Mockito.spy(
           new AwsPriceStorageListComposerLoader(s3listLoader, s3DeepArchivelistLoader)
    );

    @BeforeEach
    public void init() throws IOException {
        Mockito.doReturn(S3_PRICE_LIST_JSON).when(s3listLoader).readStringFromURL(Mockito.anyString());
        Mockito.doReturn(DEEP_ARCHIVE_PRICE_LIST).when(s3DeepArchivelistLoader).readStringFromURL(Mockito.anyString());
    }

    @Test
    public void loadS3FullPriceListShouldTakeOnlyExpectedResultsTest() {
        final Map<String, StoragePricing> pricingMap = s3listLoader.loadFullPriceList();
        Assertions.assertNotNull(pricingMap);
        Assertions.assertEquals(1, pricingMap.size());
        final Map<String, List<StoragePricing.StoragePricingEntity>> prices =
                pricingMap.get(US_EAST_1_REGION).getPrices();
        Assertions.assertEquals(1, prices.size());
        Assertions.assertEquals(3, prices.get(STANDARD_SC).size());
        Assertions.assertEquals(2.3, prices.get(STANDARD_SC).get(0).getPriceCentsPerGb().doubleValue());
        Assertions.assertEquals(2.2, prices.get(STANDARD_SC).get(1).getPriceCentsPerGb().doubleValue());
        Assertions.assertEquals(2.1, prices.get(STANDARD_SC).get(2).getPriceCentsPerGb().doubleValue());
    }

    @Test
    public void loadDeepArchiveFullPriceListShouldTakeOnlyExpectedResultsTest() {
        final Map<String, StoragePricing> pricingMap = s3DeepArchivelistLoader.loadFullPriceList();
        Assertions.assertNotNull(pricingMap);
        Assertions.assertEquals(1, pricingMap.size());
        final Map<String, List<StoragePricing.StoragePricingEntity>> prices =
                pricingMap.get(US_EAST_1_REGION).getPrices();
        Assertions.assertEquals(1, prices.size());
        Assertions.assertEquals(1, prices.get(DEEP_ARCHIVE_SC).size());
        Assertions.assertEquals(0.099, prices.get(DEEP_ARCHIVE_SC).get(0).getPriceCentsPerGb().doubleValue());
    }

    @Test
    public void loadFullPriceListForComposerLoaderShouldReturnUnitedResultsTest() throws Exception {
        final Map<String, StoragePricing> pricingMap = composeListLoader.loadFullPriceList();
        Assertions.assertNotNull(pricingMap);
        Assertions.assertEquals(1, pricingMap.size());
        final Map<String, List<StoragePricing.StoragePricingEntity>> prices =
                pricingMap.get(US_EAST_1_REGION).getPrices();
        Assertions.assertEquals(2, prices.size());
        Assertions.assertEquals(3, prices.get(STANDARD_SC).size());
        Assertions.assertEquals(2.3, prices.get(STANDARD_SC).get(0).getPriceCentsPerGb().doubleValue());
        Assertions.assertEquals(2.2, prices.get(STANDARD_SC).get(1).getPriceCentsPerGb().doubleValue());
        Assertions.assertEquals(2.1, prices.get(STANDARD_SC).get(2).getPriceCentsPerGb().doubleValue());
        Assertions.assertEquals(1, prices.get(DEEP_ARCHIVE_SC).size());
        Assertions.assertEquals(0.099, prices.get(DEEP_ARCHIVE_SC).get(0).getPriceCentsPerGb().doubleValue());
    }
}
