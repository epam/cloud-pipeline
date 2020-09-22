package com.epam.pipeline.dts.common.service;

import com.epam.pipeline.client.pipeline.CloudPipelineAPI;
import com.epam.pipeline.client.pipeline.CloudPipelineApiBuilder;
import com.epam.pipeline.dts.transfer.model.pipeline.PipelineCredentials;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.utils.QueryUtils;
import com.epam.pipeline.vo.EntityVO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CloudPipelineAPIClient {
    
    private final CloudPipelineAPI cloudPipelineAPI;

    public static CloudPipelineAPIClient from(final String apiUrl, final String apiToken) {
        return new CloudPipelineAPIClient(
                new CloudPipelineApiBuilder(0, 0, apiUrl, apiToken)
                        .buildClient());
    }

    public static CloudPipelineAPIClient from(final PipelineCredentials credentials) {
        return CloudPipelineAPIClient.from(credentials.getApi(), credentials.getApiToken());
    }

    public List<MetadataEntry> loadMetadataEntry(final List<EntityVO> entities) {
        return ListUtils.emptyIfNull(QueryUtils.execute(cloudPipelineAPI.loadFolderMetadata(entities)));
    }

    public Optional<MetadataEntry> findMetadataEntry(final EntityVO entity) {
        return loadMetadataEntry(Collections.singletonList(entity)).stream()
                .findFirst();
    }

    public Optional<MetadataEntry> findMetadataEntry(final Long id, final AclClass aclClass) {
        return findMetadataEntry(new EntityVO(id, aclClass));
    }

    public Optional<MetadataEntry> findUserMetadataEntry(final Long id) {
        return findMetadataEntry(id, AclClass.PIPELINE_USER);
    }

    public Optional<PipelineUser> whoami() {
        return Optional.ofNullable(QueryUtils.execute(cloudPipelineAPI.whoami()));
    }

    public Optional<String> getUserMetadataValueByKey(final String key) {
        return whoami()
                .map(PipelineUser::getId)
                .flatMap(this::findUserMetadataEntry)
                .map(MetadataEntry::getData)
                .map(data -> data.get(key))
                .map(PipeConfValue::getValue);
    }
}
