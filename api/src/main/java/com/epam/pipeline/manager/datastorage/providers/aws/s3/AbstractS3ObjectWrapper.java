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

package com.epam.pipeline.manager.datastorage.providers.aws.s3;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.S3VersionSummary;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.manager.datastorage.providers.ProviderUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractS3ObjectWrapper {

    public static AbstractS3ObjectWrapper getWrapper(S3VersionSummary versionSummary) {
        return new S3VersionWrapper(versionSummary);
    }

    public static AbstractS3ObjectWrapper getWrapper(S3ObjectSummary objectSummary) {
        return new S3FileWrapper(objectSummary);
    }

    public DataStorageFile convertToStorageFile(String requestPath, String prefix) {
        String relativePath = getKey();
        if ((relativePath.endsWith(ProviderUtils.DELIMITER) && relativePath.equals(requestPath))
                || StringUtils.endsWithIgnoreCase(relativePath,
                ProviderUtils.FOLDER_TOKEN_FILE.toLowerCase())) {
            return null;
        }
        if (relativePath.endsWith(ProviderUtils.DELIMITER)) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        String fileName = relativePath.substring(requestPath.length());
        DataStorageFile file = new DataStorageFile();
        file.setName(fileName);
        file.setPath(ProviderUtils.removePrefix(relativePath, prefix));
        file.setSize(getSize());
        file.setVersion(getVersion());
        file.setChanged(S3Constants.getAwsDateFormat().format(getChanged()));
        file.setDeleteMarker(getDeleteMarker());
        Map<String, String> labels = new HashMap<>();
        if (getStorageClass() != null) {
            labels.put("StorageClass", getStorageClass());
        }
        file.setLabels(labels);
        return file;
    }

    protected abstract String getStorageClass();
    protected abstract Long getSize();
    protected abstract String getVersion();
    protected abstract String getKey();
    protected abstract Date getChanged();
    protected abstract Boolean getDeleteMarker();


    static class S3FileWrapper extends AbstractS3ObjectWrapper {
        private S3ObjectSummary objectSummary;

        S3FileWrapper(S3ObjectSummary objectSummary) {
            this.objectSummary = objectSummary;
        }

        @Override protected String getStorageClass() {
            return objectSummary.getStorageClass();
        }

        @Override protected Long getSize() {
            return objectSummary.getSize();
        }

        @Override protected String getVersion() {
            return null;
        }

        @Override protected String getKey() {
            return objectSummary.getKey();
        }

        @Override protected Date getChanged() {
            return objectSummary.getLastModified();
        }

        @Override protected Boolean getDeleteMarker() {
            return null;
        }
    }

    static class S3VersionWrapper extends AbstractS3ObjectWrapper {
        private S3VersionSummary versionSummary;

        S3VersionWrapper(S3VersionSummary versionSummary) {
            this.versionSummary = versionSummary;
        }

        @Override protected String getStorageClass() {
            return versionSummary.getStorageClass();
        }

        @Override protected Long getSize() {
            return versionSummary.getSize();
        }

        @Override protected String getVersion() {
            return versionSummary.getVersionId();
        }

        @Override protected String getKey() {
            return versionSummary.getKey();
        }

        @Override protected Date getChanged() {
            return versionSummary.getLastModified();
        }

        @Override protected Boolean getDeleteMarker() {
            return versionSummary.isDeleteMarker();
        }
    }
}
