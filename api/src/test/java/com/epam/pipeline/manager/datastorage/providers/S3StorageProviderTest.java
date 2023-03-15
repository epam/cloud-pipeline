/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import com.amazonaws.services.s3.waiters.AmazonS3Waiters;
import com.amazonaws.waiters.Waiter;
import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3Helper;
import com.epam.pipeline.manager.datastorage.providers.aws.s3.S3StorageProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class S3StorageProviderTest extends AbstractSpringTest {

    private static final long REGION_ID = 1L;
    private static final String REGION_CODE = "us-east-1";
    private static final String REGION_CORS = "[" +
            "  {" +
            "    \"AllowedOrigins\": [\"string\"]," +
            "    \"AllowedMethods\": [\"PUT\", \"GET\"]," +
            "    \"AllowedHeaders\": [\"string\"]," +
            "    \"MaxAgeSeconds\": 3000," +
            "    \"ExposeHeaders\": [\"string\"]" +
            "  }" +
            "]";
    private static final String BUCKET = "bucketname";

    @MockBean
    private CloudRegionManager cloudRegionManager;

    @SpyBean
    private S3StorageProvider s3StorageProvider;

    @Autowired
    private MessageHelper messageHelper;

    @Mock
    private AmazonS3 amazonClient;

    @Before
    public void setUp() {
        final StorageEventCollector events = mock(StorageEventCollector.class);

        final S3Helper s3Helper = spy(new S3Helper(events, messageHelper));
        doReturn(s3Helper).when(s3StorageProvider).getS3Helper(any());
        doReturn(amazonClient).when(s3Helper).getDefaultS3Client();

        final AmazonS3Waiters waiters = mock(AmazonS3Waiters.class);
        doReturn(waiters).when(amazonClient).waiters();
        doReturn(mock(Waiter.class)).when(waiters).bucketExists();

        final Bucket bucket = mock(Bucket.class);
        doReturn(bucket).when(amazonClient).createBucket(any(CreateBucketRequest.class));
        doReturn(BUCKET).when(bucket).getName();

        doReturn(region()).when(cloudRegionManager).getAwsRegion(any());
    }

    @Test
    public void createBucketWithSpecifiedCorsPolicy() {
        final S3bucketDataStorage storage = new S3bucketDataStorage(1L, BUCKET, BUCKET);
        storage.setRegionId(REGION_ID);
        final String createdBucketName = s3StorageProvider.createStorage(storage);
        Assert.assertEquals(BUCKET, createdBucketName);

        s3StorageProvider.postCreationProcessing(storage);

        verify(amazonClient).setBucketCrossOriginConfiguration(any(), any());
    }

    private AwsRegion region() {
        final AwsRegion region = new AwsRegion();
        region.setId(REGION_ID);
        region.setRegionCode(REGION_CODE);
        region.setCorsRules(REGION_CORS);
        return region;
    }
}
