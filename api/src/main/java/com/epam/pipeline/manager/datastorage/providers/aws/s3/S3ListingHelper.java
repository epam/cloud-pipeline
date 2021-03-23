package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.amazonaws.services.s3.model.VersionListing;
import com.amazonaws.util.StringUtils;
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

public final class S3ListingHelper {

    private S3ListingHelper() { }
    
    public static Stream<DataStorageFile> files(final AmazonS3 client, final String bucket, final String path) {
        final PageIterator iterator = new PageIterator(client, bucket, path);
        final Spliterator<List<DataStorageFile>> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false).flatMap(List::stream);
    }
    
    @RequiredArgsConstructor
    private static class PageIterator implements Iterator<List<DataStorageFile>> {

        private final AmazonS3 client;
        private final String bucket;
        private final String path;

        private String nextKeyMarker;
        private String nextVersionIdMarker;
        private List<DataStorageFile> items;

        @Override
        public boolean hasNext() {
            return items == null
                    || org.apache.commons.lang3.StringUtils.isNotBlank(nextKeyMarker)
                    || org.apache.commons.lang3.StringUtils.isNotBlank(nextVersionIdMarker);
        }

        @Override
        public List<DataStorageFile> next() {
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
                    .filter(summary -> matchesPath(summary) && isLatest(summary))
                    .map(this::toDataStorageFile)
                    .collect(Collectors.toList());
            return items;
        }

        private DataStorageFile toDataStorageFile(final S3VersionSummary summary) {
            final DataStorageFile file = new DataStorageFile();
            file.setName(summary.getKey());
            file.setPath(summary.getKey());
            if (summary.getVersionId() != null && !summary.getVersionId().equals("null")) {
                file.setVersion(summary.getVersionId());
            }
            return file;
        }

        private boolean matchesPath(final S3VersionSummary summary) {
            return StringUtils.isNullOrEmpty(path)
                    || (path.endsWith(ProviderUtils.DELIMITER) && summary.getKey().startsWith(path))
                    || summary.getKey().equals(path);
        }

        private boolean isLatest(final S3VersionSummary summary) {
            return summary.isLatest()
                    && !summary.isDeleteMarker();
        }
    }
}
