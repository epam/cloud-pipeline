/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class S3HelperTest {

    private static final String BUCKET = "bucket";
    private static final String OLD_PATH = "oldPath";
    private static final String NEW_PATH = "newPath";
    private static final String VERSION = "version";
    private static final String NO_VERSION = null;
    private static final long EXCEEDED_OBJECT_SIZE = Long.MAX_VALUE;
    private static final String SIZE_EXCEEDS_EXCEPTION_MESSAGE = "size exceeds the limit";

    private final AmazonS3 amazonS3 = mock(AmazonS3.class);
    private final S3Helper helper = spy(new S3Helper());

    @Before
    public void setUp() {
        doReturn(amazonS3).when(helper).getDefaultS3Client();
    }

    @Test
    public void testPopulateBucketPolicy() throws IOException {
        String policyStr = "{"
                           + "  \"Version\": \"2008-10-17\","
                           + "  \"Id\": \"CloudPipelineS3ACL\","
                           + "  \"Statement\": ["
                           + "    {"
                           + "      \"Sid\": \"CloudPipelineS3ACLSubnets\","
                           + "      \"Effect\": \"Deny\","
                           + "      \"Principal\": \"*\","
                           + "      \"Action\": \"s3:*\","
                           + "      \"Resource\": ["
                           + "        \"arn:aws:s3:::[BUCKET_NAME]/*\","
                           + "        \"arn:aws:s3:::[BUCKET_NAME]\""
                           + "      ],"
                           + "      \"Condition\": {"
                           + "        \"StringNotEquals\": {"
                           + "          \"aws:SourceVpce\": \"vpce-[CLOUD_PIPELINE_VPCE_ID]\""
                           + "        },"
                           + "        \"NotIpAddress\": {"
                           + "          \"aws:SourceIp\": [\"[ALLOWED_CIDRS]\"]"
                           + "        },"
                           + "        \"StringNotLike\": {"
                           + "          \"aws:arn\": ["
                           + "            \"arn:aws:iam::036455598313:user/NGS_service\","
                           + "            \"arn:aws:sts::036455598313:assumed-role/[S3_ACCESS_ROLE_NAME]/*\""
                           + "          ]"
                           + "        }"
                           + "      }"
                           + "    }"
                           + "  ]"
                           + "}";

        List<String> testAllowedCidrs = Arrays.asList("199.245.32.0/24", "192.234.111.0/24", "198.73.159.0/24",
                                                      "8.10.249.0/24", "77.75.64.0/23", "77.75.66.0/23",
                                                      "193.202.91.0/24");

        S3Helper helper = new S3Helper();
        ObjectMapper objectMapper = new ObjectMapper();

        String populatedPolicyString = helper.populateBucketPolicy("testBucket", policyStr, testAllowedCidrs, true);
        JsonNode populatedPolicyObject = objectMapper.readTree(populatedPolicyString);
        JsonNode cidrs = populatedPolicyObject.get("Statement").get(0).get("Condition").get("NotIpAddress")
            .get("aws:SourceIp");
        cidrs.elements().forEachRemaining(c -> assertTrue(testAllowedCidrs.contains(c.asText())));

        populatedPolicyString = helper.populateBucketPolicy("testBucket", policyStr, null, true);
        populatedPolicyObject = objectMapper.readTree(populatedPolicyString);
        cidrs = populatedPolicyObject.get("Statement").get(0).get("Condition").get("NotIpAddress").get("aws:SourceIp");

        assertEquals(1, cidrs.size());
        assertEquals("0.0.0.0/0", cidrs.get(0).asText());
    }

    @Test
    public void testMoveFileShouldThrowIfFileSizeExceedsTheLimit() {
        final ObjectListing singleFileListing = new ObjectListing();
        singleFileListing.setCommonPrefixes(Collections.singletonList(OLD_PATH));
        when(amazonS3.listObjects(any(ListObjectsRequest.class))).thenReturn(singleFileListing);
        final ObjectMetadata fileMetadata = new ObjectMetadata();
        fileMetadata.setContentLength(EXCEEDED_OBJECT_SIZE);
        when(amazonS3.getObjectMetadata(any())).thenReturn(fileMetadata);

        assertThrows(e -> e instanceof DataStorageException && e.getMessage().contains(SIZE_EXCEEDS_EXCEPTION_MESSAGE),
            () -> helper.moveFile(BUCKET, OLD_PATH, NEW_PATH));
    }

    @Test
    public void testMoveFileShouldCopyAndDeleteTheOriginalFile() {
        final ObjectListing singleFileListing = new ObjectListing();
        singleFileListing.setCommonPrefixes(Collections.singletonList(OLD_PATH));
        when(amazonS3.listObjects(any(ListObjectsRequest.class))).thenReturn(singleFileListing);
        final ObjectMetadata fileMetadata = new ObjectMetadata();
        when(amazonS3.getObjectMetadata(any())).thenReturn(fileMetadata);

        helper.moveFile(BUCKET, OLD_PATH, NEW_PATH);

        verify(amazonS3).copyObject(argThat(hasSourceAndDestination(OLD_PATH, NEW_PATH)));
        final Map<String, String> pathVersionMap = new HashMap<>();
        pathVersionMap.put(OLD_PATH, NO_VERSION);
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(pathVersionMap)));
    }

    @Test
    public void testRestoreFileVersionShouldThrowIfFileSizeExceedsTheLimit() {
        final ObjectMetadata fileMetadata = new ObjectMetadata();
        fileMetadata.setContentLength(EXCEEDED_OBJECT_SIZE);
        when(amazonS3.getObjectMetadata(any())).thenReturn(fileMetadata);

        assertThrows(e -> e instanceof DataStorageException && e.getMessage().contains(SIZE_EXCEEDS_EXCEPTION_MESSAGE),
            () -> helper.restoreFileVersion(BUCKET, OLD_PATH, VERSION));
    }

    @Test
    public void testRestoreFileShouldCopyFileAndThenDeleteTheOriginalFile() {
        final ObjectMetadata fileMetadata = new ObjectMetadata();
        when(amazonS3.getObjectMetadata(any())).thenReturn(fileMetadata);

        helper.restoreFileVersion(BUCKET, OLD_PATH, VERSION);

        verify(amazonS3).copyObject(argThat(hasSourceAndDestination(OLD_PATH, OLD_PATH)));
        final Map<String, String> pathVersionMap = new HashMap<>();
        pathVersionMap.put(OLD_PATH, VERSION);
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(pathVersionMap)));
    }

    @Test
    public void testMoveFolderShouldThrowIfAtLeastOneOfItsFilesSizeExceedTheLimit() {
        final ObjectListing sourceListing = new ObjectListing();
        sourceListing.setCommonPrefixes(Collections.singletonList(OLD_PATH));
        final ObjectListing destinationListing = new ObjectListing();
        destinationListing.setCommonPrefixes(Collections.emptyList());
        final ObjectListing bucketListing = spy(new ObjectListing());
        final S3ObjectSummary fileSummary = new S3ObjectSummary();
        fileSummary.setKey(OLD_PATH + "/someBigFile");
        fileSummary.setSize(EXCEEDED_OBJECT_SIZE);
        when(bucketListing.getObjectSummaries()).thenReturn(Collections.singletonList(fileSummary));
        when(amazonS3.listObjects(any(ListObjectsRequest.class)))
                .thenReturn(sourceListing, destinationListing, bucketListing);

        assertThrows(e -> e instanceof DataStorageException && e.getMessage().contains(SIZE_EXCEEDS_EXCEPTION_MESSAGE),
            () -> helper.moveFolder(BUCKET, OLD_PATH, NEW_PATH));
    }

    @Test
    public void testMoveFolderShouldCopyAndDeleteAllOfItsFiles() {
        final String firstFileOldPath = OLD_PATH + "/firstFile";
        final String firstFileNewPath = NEW_PATH + "/firstFile";
        final String secondFileOldPath = OLD_PATH + "/secondFile";
        final String secondFileNewPath = NEW_PATH + "/secondFile";
        final ObjectListing sourceListing = new ObjectListing();
        sourceListing.setCommonPrefixes(Collections.singletonList(OLD_PATH));
        final ObjectListing destinationListing = new ObjectListing();
        destinationListing.setCommonPrefixes(Collections.emptyList());
        final ObjectListing bucketListing = spy(new ObjectListing());
        final S3ObjectSummary firstFileSummary = new S3ObjectSummary();
        firstFileSummary.setKey(firstFileOldPath);
        final S3ObjectSummary secondFileSummary = new S3ObjectSummary();
        secondFileSummary.setKey(secondFileOldPath);
        when(bucketListing.getObjectSummaries()).thenReturn(Arrays.asList(firstFileSummary, secondFileSummary));
        when(amazonS3.listObjects(any(ListObjectsRequest.class)))
                .thenReturn(sourceListing, destinationListing, bucketListing);

        helper.moveFolder(BUCKET, OLD_PATH, NEW_PATH);

        verify(amazonS3).copyObject(argThat(hasSourceAndDestination(firstFileOldPath, firstFileNewPath)));
        verify(amazonS3).copyObject(argThat(hasSourceAndDestination(secondFileOldPath, secondFileNewPath)));
        final Map<String, String> pathVersionMap = new HashMap<>();
        pathVersionMap.put(firstFileOldPath, NO_VERSION);
        pathVersionMap.put(secondFileOldPath, NO_VERSION);
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(pathVersionMap)));
    }

    private BaseMatcher<CopyObjectRequest> hasSourceAndDestination(final String source, final String destination) {
        return new BaseMatcher<CopyObjectRequest>() {
            @Override
            public boolean matches(final Object item) {
                final CopyObjectRequest casted = (CopyObjectRequest) item;
                return Objects.equals(casted.getSourceKey(), source)
                        && Objects.equals(casted.getDestinationKey(), destination);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Copy object request doesn't have required source and destination");
            }
        };
    }

    private BaseMatcher<DeleteObjectsRequest> hasPathsAndVersions(final Map<String, String> pathVersionMap) {
        return new BaseMatcher<DeleteObjectsRequest>() {
            @Override
            public boolean matches(final Object item) {
                final DeleteObjectsRequest deleteObjectRequest = (DeleteObjectsRequest) item;
                return pathVersionMap.entrySet().stream()
                        .allMatch(pathVersion -> {
                            final String expectedPath = pathVersion.getKey();
                            final String expectedVersion = pathVersion.getValue();
                            return deleteObjectRequest.getKeys().stream()
                                    .filter(keyVersion -> Objects.equals(keyVersion.getKey(), expectedPath))
                                    .findFirst()
                                    .filter(keyVersion -> Objects.equals(keyVersion.getVersion(), expectedVersion))
                                    .isPresent();
                        });
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Delete object request doesn't have some of the required key-version pairs");
            }
        };
    }
}
