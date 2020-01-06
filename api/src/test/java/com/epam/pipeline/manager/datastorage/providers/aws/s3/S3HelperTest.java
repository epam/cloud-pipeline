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
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.data.storage.RestoreFolderVO;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"PMD.TooManyStaticImports"})
public class S3HelperTest {

    private static final String BUCKET = "bucket";
    private static final String OLD_PATH = "oldPath";
    private static final String NEW_PATH = "newPath";
    private static final String VERSION = "version";
    private static final String OLD_VERSION = "oldVersion";
    private static final String NO_VERSION = null;
    private static final String FIRST_FILE_PATH = OLD_PATH + "/firstFile.jpg";
    private static final String SECOND_FILE_PATH = OLD_PATH + "/" + NEW_PATH + "/secondFile.png";
    private static final String JPG_PATTERN = "*.jpg";
    private static final long EXCEEDED_OBJECT_SIZE = Long.MAX_VALUE;
    private static final String SIZE_EXCEEDS_EXCEPTION_MESSAGE = "size exceeds the limit";

    private final AmazonS3 amazonS3 = mock(AmazonS3.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);
    private final S3Helper helper = spy(new S3Helper(messageHelper));

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

        S3Helper helper = new S3Helper(messageHelper);
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
    public void testRestoreFolderWithExcludeListShouldNotRestoreExcludeFiles() {
        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(true,
                null, Collections.singletonList(JPG_PATTERN), null));
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(
                Collections.singletonMap(SECOND_FILE_PATH, VERSION))));
    }

    @Test
    public void testRestoreFolderWithIncludeListAndNoRecursionShouldRestoreOnlyIncludeFiles() {
        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(false,
                Collections.singletonList(JPG_PATTERN), null, null));
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(
                Collections.singletonMap(FIRST_FILE_PATH, VERSION))));
    }

    @Test
    public void testRestoreFolderWithIncludeListAndWithRecursionShouldRestoreOnlyIncludeFiles() {
        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(true,
                Collections.singletonList(JPG_PATTERN), null, null));
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(
                Collections.singletonMap(FIRST_FILE_PATH, VERSION))));
    }

    @Test
    public void testRestoreFolderShouldRestoreOnlyFilesWithDeleteMarker(){
        final S3VersionSummary withoutDeleteMarkerFileSummary = new S3VersionSummary();
        withoutDeleteMarkerFileSummary.setKey(SECOND_FILE_PATH);
        withoutDeleteMarkerFileSummary.setIsDeleteMarker(false); // File should NOT be restore
        withoutDeleteMarkerFileSummary.setVersionId(VERSION);
        withoutDeleteMarkerFileSummary.setLastModified(new Date());
        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(false, null, null,
                withoutDeleteMarkerFileSummary));
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(
                Collections.singletonMap(OLD_PATH + "/firstFile.jpg", VERSION))));
    }

    @Test
    public void testRestoreFolderShouldRestoreOnlyLastFileVersion(){
        final S3VersionSummary anotherFirstFileSummary = new S3VersionSummary();
        anotherFirstFileSummary.setKey(FIRST_FILE_PATH);
        anotherFirstFileSummary.setIsDeleteMarker(true);
        anotherFirstFileSummary.setVersionId(OLD_VERSION);
        anotherFirstFileSummary.setLastModified(LocalDate.parse("1995-01-24").toDate()); // Old version of file
        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(false, null, null,
                anotherFirstFileSummary));
        verify(amazonS3).deleteObjects(argThat(hasPathsAndVersions(
                Collections.singletonMap(FIRST_FILE_PATH, VERSION))));
    }

    @Test
    public void testRestoreFolderWithRecursionShouldLoopAllFolders() {
        final Map<String, String> pathVersionMap = new HashMap<>();
        pathVersionMap.put(FIRST_FILE_PATH, VERSION);
        pathVersionMap.put(SECOND_FILE_PATH, VERSION);

        helper.restoreFolder(BUCKET, OLD_PATH, presettingForRestoreFolderMethodTests(true, null, null, null));
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
                return deleteObjectRequest.getKeys().stream()
                        .allMatch(keyVersion -> {
                            final String expectedPath = keyVersion.getKey();
                            final String expectedVersion = keyVersion.getVersion();
                            return pathVersionMap.entrySet().stream()
                                    .filter(pathVersion -> Objects.equals(pathVersion.getKey(), expectedPath))
                                    .findFirst()
                                    .filter(pathVersion -> Objects.equals(pathVersion.getValue(), expectedVersion))
                                    .isPresent();
                        });
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Delete object request doesn't match some of the passed key-version pairs");
            }
        };
    }

    private RestoreFolderVO presettingForRestoreFolderMethodTests(final boolean recursive,
                                                                  final List<String> includeList,
                                                                  final List<String> excludeList,
                                                                  final S3VersionSummary optionalFileSummary) {
        final VersionListing firstFolder = spy(new VersionListing());
        firstFolder.setCommonPrefixes(Collections.singletonList(OLD_PATH + "/" + NEW_PATH));
        final VersionListing secondFolder = spy(new VersionListing());
        final S3VersionSummary firstFileSummary = new S3VersionSummary();
        firstFileSummary.setKey(FIRST_FILE_PATH);
        firstFileSummary.setIsDeleteMarker(true);
        firstFileSummary.setVersionId(VERSION);
        firstFileSummary.setLastModified(new Date());
        final S3VersionSummary secondFileSummary = new S3VersionSummary();
        secondFileSummary.setKey(SECOND_FILE_PATH);
        secondFileSummary.setIsDeleteMarker(true);
        secondFileSummary.setVersionId(VERSION);
        secondFileSummary.setLastModified(new Date());
        when(firstFolder.getCommonPrefixes()).thenReturn(Collections.singletonList(OLD_PATH + "/" + NEW_PATH));
        when(firstFolder.getVersionSummaries()).thenReturn(
                Optional.ofNullable(optionalFileSummary)
                        .map(fileSummary -> Arrays.asList(firstFileSummary, fileSummary))
                        .orElse(Collections.singletonList(firstFileSummary))
        );
        when(secondFolder.getVersionSummaries()).thenReturn(Collections.singletonList(secondFileSummary));
        when(amazonS3.listVersions(any(ListVersionsRequest.class))).thenReturn(firstFolder, firstFolder, secondFolder);
        final RestoreFolderVO restoreFolderVO = new RestoreFolderVO();
        restoreFolderVO.setRecursively(recursive);
        restoreFolderVO.setIncludeList(includeList);
        restoreFolderVO.setExcludeList(excludeList);
        return restoreFolderVO;
    }
}
