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

package com.epam.pipeline.manager.datastorage.providers;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3StorageProviderTest extends AbstractSpringTest {

    public static final long REGION_ID = 1L;
    private final String bucketName = "bucketName";

    @SpyBean
    private S3StorageProvider s3StorageProvider;

    @SpyBean
    private S3Helper s3Helper;

    @Mock
    private AmazonS3 amazonClient;

    @Mock
    private Bucket bucket;

    @MockBean
    private CloudRegionManager cloudRegionManager;

    @Before
    public void setUp() {
        when(bucket.getName()).thenReturn(bucketName);
        when(amazonClient.createBucket(any(CreateBucketRequest.class)))
                .thenReturn(bucket);
        when(s3Helper.getDefaultS3Client())
                .thenReturn(amazonClient);
        AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode("us-east-1");
        region.setCorsRules("[" +
                "  {" +
                "    \"AllowedOrigins\": [\"string\"]," +
                "    \"AllowedMethods\": [\"PUT\", \"GET\"]," +
                "    \"AllowedHeaders\": [\"string\"]," +
                "    \"MaxAgeSeconds\": 3000," +
                "    \"ExposeHeaders\": [\"string\"]" +
                "  }" +
                "]");
        when(cloudRegionManager.getAwsRegion(any())).thenReturn(region);
        doReturn(s3Helper).when(s3StorageProvider).getS3Helper(any());
    }

    @Test
    public void createBucketWithSpecifiedCorsPolicy() {
        final S3bucketDataStorage storage = new S3bucketDataStorage(1L, bucketName, bucketName);
        storage.setRegionId(REGION_ID);
        final String createdBucketName = s3StorageProvider.createStorage(storage);

        Assert.assertEquals(bucketName, createdBucketName);
        verify(amazonClient).setBucketCrossOriginConfiguration(any(), any());
    }
}