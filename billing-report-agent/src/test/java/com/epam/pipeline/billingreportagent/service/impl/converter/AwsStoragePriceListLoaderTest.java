package com.epam.pipeline.billingreportagent.service.impl.converter;

import com.epam.pipeline.billingreportagent.model.billing.StoragePricing;
import com.epam.pipeline.billingreportagent.model.pricing.AwsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"checkstyle:MagicNumber", "PMD.AvoidDuplicateLiterals"})
class AwsStoragePriceListLoaderTest {

    public static final String US_EAST_1_REGION = "us-east-1";
    private static final String US_EAST_1_LOCATION = "US East (N. Virginia)";
    private static final String EU_CENTRAL_1_REGION = "eu-central-1";
    private static final String EU_CENTRAL_1_LOCATION = "EU (Frankfurt)";
    private static final String GENERAL_PURPOSE = "General Purpose";
    private static final String TERM_KEY = "term";
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
            new AwsStoragePriceListLoader(AwsService.S3_SERVICE,
                    PriceLoadingMode.JSON, "https://localhost/mock.json")
    );

    private final AwsStoragePriceListLoader s3DeepArchivelistLoader = Mockito.spy(
            new AwsStoragePriceListLoader(AwsService.S3_GLACIER_SERVICE,
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

    @Test
    public void loadS3FullPriceListShouldTakeOnlyBilledTimedStorageProductsTest() throws IOException {
        final String priceList = priceList("AmazonS3",
                product("STANDARD1", US_EAST_1_LOCATION, GENERAL_PURPOSE, "Standard", "TimedStorage-ByteHrs",
                        tier(0, "51200", "0.023"), tier(51200, "512000", "0.022"), tier(512000, "Inf", "0.021")),
                product("ANNOTATIONS1", US_EAST_1_LOCATION, GENERAL_PURPOSE, "Annotations",
                        "Annotation-TimedStorage-ByteHrs", tier(0, "Inf", "0.023")),
                product("UNKNOWN1", US_EAST_1_LOCATION, GENERAL_PURPOSE, "Unknown Volume Type",
                        "TimedStorage-ByteHrs", tier(0, "Inf", "0.05")),
                product("GIR1", US_EAST_1_LOCATION, "Archive Instant Retrieval", "Glacier Instant Retrieval",
                        "TimedStorage-GIR-ByteHrs", tier(0, "Inf", "0.004")),
                product("GLACIER1", US_EAST_1_LOCATION, "Archive", "Amazon Glacier",
                        "TimedStorage-GlacierByteHrs", tier(0, "Inf", "0.0036")),
                product("STANDARD2", EU_CENTRAL_1_LOCATION, GENERAL_PURPOSE, "Standard", "EUC1-TimedStorage-ByteHrs",
                        tier(0, "Inf", "0.0245")),
                product("ANNOTATIONS2", EU_CENTRAL_1_LOCATION, GENERAL_PURPOSE, "Annotations",
                        "EUC1-Annotation-TimedStorage-ByteHrs", tier(0, "Inf", "0.0245")));
        final AwsStoragePriceListLoader loader = loader(AwsService.S3_SERVICE, priceList);

        final Map<String, StoragePricing> pricingMap = loader.loadFullPriceList();

        Assertions.assertEquals(2, pricingMap.size());
        final Map<String, List<StoragePricing.StoragePricingEntity>> usEast1 =
                pricingMap.get(US_EAST_1_REGION).getPrices();
        Assertions.assertEquals(3, usEast1.size());
        assertPrices(usEast1.get(STANDARD_SC), 2.3, 2.2, 2.1);
        assertPrices(usEast1.get("GLACIER_IR"), 0.4);
        assertPrices(usEast1.get("GLACIER"), 0.36);
        final Map<String, List<StoragePricing.StoragePricingEntity>> euCentral1 =
                pricingMap.get(EU_CENTRAL_1_REGION).getPrices();
        Assertions.assertEquals(1, euCentral1.size());
        assertPrices(euCentral1.get(STANDARD_SC), 2.45);
    }

    @Test
    public void loadEfsFullPriceListShouldTakeOnlyStandardTimedStorageTest() throws IOException {
        final String priceList = priceList("AmazonEFS",
                product("EFS1", US_EAST_1_LOCATION, GENERAL_PURPOSE, null, "USE1-TimedStorage-ByteHrs",
                        tier(0, "Inf", "0.30")),
                product("EFS2", US_EAST_1_LOCATION, "Archive", null, "USE1-ArchiveTimedStorage-ByteHrs",
                        tier(0, "Inf", "0.008")),
                product("EFS3", US_EAST_1_LOCATION, "Infrequent Access", null, "USE1-IATimedStorage-ByteHrs",
                        tier(0, "Inf", "0.016")));
        final AwsStoragePriceListLoader loader = loader(AwsService.EFS_SERVICE, priceList);

        final Map<String, StoragePricing> pricingMap = loader.loadFullPriceList();

        Assertions.assertEquals(1, pricingMap.size());
        final Map<String, List<StoragePricing.StoragePricingEntity>> prices =
                pricingMap.get(US_EAST_1_REGION).getPrices();
        Assertions.assertEquals(1, prices.size());
        assertPrices(prices.get(STANDARD_SC), 30.0);
    }

    private static AwsStoragePriceListLoader loader(final AwsService service,
                                                    final String priceList) throws IOException {
        final AwsStoragePriceListLoader loader = Mockito.spy(
                new AwsStoragePriceListLoader(service, PriceLoadingMode.JSON, "https://localhost/mock.json"));
        Mockito.doReturn(priceList).when(loader).readStringFromURL(Mockito.anyString());
        return loader;
    }

    private static void assertPrices(final List<StoragePricing.StoragePricingEntity> prices,
                                     final double... expectedCentsPerGb) {
        Assertions.assertNotNull(prices);
        Assertions.assertArrayEquals(expectedCentsPerGb, prices.stream()
                .mapToDouble(price -> price.getPriceCentsPerGb().doubleValue())
                .toArray());
    }

    private static Map<String, Object> tier(final long beginRange, final String endRange, final String usd) {
        final Map<String, Object> tier = new LinkedHashMap<>();
        tier.put("beginRange", String.valueOf(beginRange));
        tier.put("endRange", endRange);
        tier.put("unit", "GB-Mo");
        tier.put("pricePerUnit", Collections.singletonMap("USD", usd));
        return tier;
    }

    @SafeVarargs
    private static Map<String, Object> product(final String sku, final String location, final String storageClass,
                                               final String volumeType, final String usageType,
                                               final Map<String, Object>... tiers) {
        final Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("location", location);
        attributes.put("storageClass", storageClass);
        if (volumeType != null) {
            attributes.put("volumeType", volumeType);
        }
        attributes.put("usagetype", usageType);
        final Map<String, Object> product = new LinkedHashMap<>();
        product.put("sku", sku);
        product.put("productFamily", "Storage");
        product.put("attributes", attributes);
        final Map<String, Object> dimensions = new LinkedHashMap<>();
        for (int i = 0; i < tiers.length; i++) {
            dimensions.put(sku + ".TERM." + i, tiers[i]);
        }
        final Map<String, Object> term = new LinkedHashMap<>();
        term.put("sku", sku);
        term.put("priceDimensions", dimensions);
        product.put(TERM_KEY, Collections.singletonMap(sku + ".TERM", term));
        return product;
    }

    @SafeVarargs
    private static String priceList(final String offerCode,
                                    final Map<String, Object>... products) throws IOException {
        final Map<String, Object> productsBySku = new LinkedHashMap<>();
        final Map<String, Object> termsBySku = new LinkedHashMap<>();
        for (final Map<String, Object> product : products) {
            final Map<String, Object> productOnly = new LinkedHashMap<>(product);
            termsBySku.put((String) product.get("sku"), productOnly.remove(TERM_KEY));
            productsBySku.put((String) product.get("sku"), productOnly);
        }
        final Map<String, Object> priceList = new LinkedHashMap<>();
        priceList.put("offerCode", offerCode);
        priceList.put("version", "20261104174445");
        priceList.put("products", productsBySku);
        priceList.put("terms", Collections.singletonMap("OnDemand", termsBySku));
        return new ObjectMapper().writeValueAsString(priceList);
    }
}
