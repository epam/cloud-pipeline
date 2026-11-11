/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.cloud.aws;

import com.epam.pipeline.dao.datastorage.DataStorageDao;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class S3AccessPointArnResolverTest {

    private static final Long SOURCE_ID = 1L;
    private static final Long LINK_ID = 2L;
    private static final String ALIAS = "vol-test-abcdef-ext-s3alias";
    private static final String ACCESS_POINT_ARN = "arn:aws:s3:eu-west-3:123456789012:accesspoint/vol-test";
    private static final String LINK_ACCESS_POINT_ARN = "arn:aws:s3:eu-west-3:123456789012:accesspoint/vol-link";

    private final DataStorageDao dataStorageDao = mock(DataStorageDao.class);
    private final S3AccessPointArnResolver resolver = new S3AccessPointArnResolver(dataStorageDao);

    @Test
    public void shouldReturnOwnArnOfStorage() {
        final S3bucketDataStorage storage = storage(SOURCE_ID, ALIAS);
        storage.setAccessPointArn(ACCESS_POINT_ARN);

        assertEquals(ACCESS_POINT_ARN, resolver.resolve(storage));
        verify(dataStorageDao, never()).loadDataStorage(anyLong());
    }

    @Test
    public void shouldReturnNullForStorageWithoutArn() {
        assertNull(resolver.resolve(storage(SOURCE_ID, ALIAS)));
        verify(dataStorageDao, never()).loadDataStorage(anyLong());
    }

    @Test
    public void shouldReturnSourceArnForLinkInSameRoot() {
        mockSource(ACCESS_POINT_ARN);

        assertEquals(ACCESS_POINT_ARN, resolver.resolve(link(ALIAS + "/folder")));
    }

    @Test
    public void shouldPreferOwnArnOfLink() {
        mockSource(ACCESS_POINT_ARN);
        final S3bucketDataStorage link = link(ALIAS);
        link.setAccessPointArn(LINK_ACCESS_POINT_ARN);

        assertEquals(LINK_ACCESS_POINT_ARN, resolver.resolve(link));
    }

    @Test
    public void shouldNotReturnSourceArnForLinkWithMasks() {
        mockSource(ACCESS_POINT_ARN);
        final S3bucketDataStorage link = link(ALIAS);
        link.setLinkingMasks(Collections.singleton("*.csv"));

        assertNull(resolver.resolve(link));
        verify(dataStorageDao, never()).loadDataStorage(anyLong());
    }

    @Test
    public void shouldNotReturnSourceArnForLinkInAnotherRoot() {
        mockSource(ACCESS_POINT_ARN);

        assertNull(resolver.resolve(link("another-bucket")));
    }

    @Test
    public void shouldNotReturnArnForMissingOrNonS3Source() {
        when(dataStorageDao.loadDataStorage(SOURCE_ID)).thenReturn(null);
        assertNull(resolver.resolve(link(ALIAS)));

        when(dataStorageDao.loadDataStorage(SOURCE_ID))
                .thenReturn(new NFSDataStorage(SOURCE_ID, "nfs", "127.0.0.1:/path"));
        assertNull(resolver.resolve(link(ALIAS)));
    }

    private void mockSource(final String accessPointArn) {
        final S3bucketDataStorage source = storage(SOURCE_ID, ALIAS);
        source.setAccessPointArn(accessPointArn);
        when(dataStorageDao.loadDataStorage(SOURCE_ID)).thenReturn(source);
    }

    private S3bucketDataStorage link(final String path) {
        final S3bucketDataStorage link = storage(LINK_ID, path);
        link.setSourceStorageId(SOURCE_ID);
        return link;
    }

    private S3bucketDataStorage storage(final Long id, final String path) {
        return new S3bucketDataStorage(id, "storage-" + id, path);
    }
}
