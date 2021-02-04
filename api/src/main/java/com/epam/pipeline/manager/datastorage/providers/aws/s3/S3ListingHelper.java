package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.VersionListing;
import com.amazonaws.util.StringUtils;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
public class S3ListingHelper {
    
    public static Stream<DataStorageFile> files(AmazonS3 client, String bucket, String path) {
        return new S3ListingBulkIterator(client, bucket, path)
                .stream()
                .flatMap(List::stream)
                .filter(DataStorageFile.class::isInstance)
                .map(DataStorageFile.class::cast);
    }
    
    public static Stream<DataStorageFile> fileVersions(AmazonS3 client, String bucket, String path) {
        return new S3VersionsListingBulkIterator(client, bucket, path)
                .stream()
                .flatMap(List::stream)
                .filter(DataStorageFile.class::isInstance)
                .map(DataStorageFile.class::cast);
    }
    
    @RequiredArgsConstructor
    public static class S3ListingBulkIterator implements Iterator<List<AbstractDataStorageItem>> {

        private final AmazonS3 client;
        private final String bucket;
        private final String path;

        private String nextMarker;
        private List<AbstractDataStorageItem> items;

        @Override
        public boolean hasNext() {
            return items == null
                    || org.apache.commons.lang3.StringUtils.isNotBlank(nextMarker);
        }

        @Override
        public List<AbstractDataStorageItem> next() {
            final ObjectListing objectsListing = client.listObjects(new ListObjectsRequest()
                    .withBucketName(bucket)
                    .withPrefix(path)
                    .withMarker(nextMarker));
            if (objectsListing.isTruncated()) {
                nextMarker = null;
            } else {
                nextMarker = objectsListing.getNextMarker();
            }
            items = objectsListing.getObjectSummaries()
                    .stream()
                    .filter(it -> pathMatch(path, it.getKey()))
                    .map(it -> {
                        final DataStorageFile file = new DataStorageFile();
                        file.setName(it.getKey());
                        file.setPath(it.getKey());
                        return file;
                    })
                    .collect(Collectors.toList());
            return items;
        }

        private boolean pathMatch(final String path, final String key) {
            return key.startsWith(path);
        }

        public Stream<List<AbstractDataStorageItem>> stream() {
            final Spliterator<List<AbstractDataStorageItem>> spliterator = Spliterators.spliteratorUnknownSize(this, 0);
            return StreamSupport.stream(spliterator, false);
        }
    }

    @RequiredArgsConstructor
    public static class S3VersionsListingBulkIterator implements Iterator<List<AbstractDataStorageItem>> {

        private final AmazonS3 client;
        private final String bucket;
        private final String path;

        private String nextKeyMarker;
        private String nextVersionIdMarker;
        private List<AbstractDataStorageItem> items;

        @Override
        public boolean hasNext() {
            return items == null
                    || org.apache.commons.lang3.StringUtils.isNotBlank(nextKeyMarker)
                    || org.apache.commons.lang3.StringUtils.isNotBlank(nextVersionIdMarker);
        }

        @Override
        public List<AbstractDataStorageItem> next() {
            final VersionListing versionListing = client.listVersions(new ListVersionsRequest()
                    .withBucketName(bucket)
                    .withKeyMarker(nextKeyMarker)
                    .withVersionIdMarker(nextVersionIdMarker));
            if (versionListing.isTruncated()) {
                nextKeyMarker = null;
                nextVersionIdMarker = null;
            } else {
                nextKeyMarker = versionListing.getNextKeyMarker();
                nextVersionIdMarker = versionListing.getNextVersionIdMarker();
            }
            items = versionListing.getVersionSummaries()
                    .stream()
                    .filter(it -> pathMatch(path, it.getKey()))
                    .map(it -> {
                        final DataStorageFile file = new DataStorageFile();
                        file.setName(it.getKey());
                        file.setPath(it.getKey());
                        file.setVersion(it.getVersionId());
                        return file;
                    })
                    .collect(Collectors.toList());
            return items;
        }

        private boolean pathMatch(final String path, final String key) {
            return StringUtils.isNullOrEmpty(path)
                    || (path.endsWith(ProviderUtils.DELIMITER) && key.startsWith(path))
                    || key.equals(path);
        }

        public Stream<List<AbstractDataStorageItem>> stream() {
            final Spliterator<List<AbstractDataStorageItem>> spliterator = Spliterators.spliteratorUnknownSize(this, 0);
            return StreamSupport.stream(spliterator, false);
        }
    }
}
