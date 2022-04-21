package com.epam.pipeline.manager.dts;

import com.epam.pipeline.entity.dts.CreateDtsDeletionRequest;
import com.epam.pipeline.entity.dts.CreateDtsDeletionRequestStorageItem;
import com.epam.pipeline.entity.dts.DtsCreateDeletionRequest;
import com.epam.pipeline.entity.dts.DtsDeletion;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsTaskStorageItem;
import com.epam.pipeline.exception.DtsRequestException;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DtsDeletionManager {

    private final AuthManager authManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsClientBuilder clientBuilder;

    private DtsClient getDtsClient(final DtsRegistry dts) {
        final String token = getApiToken();
        return clientBuilder.createDtsClient(dts.getUrl(), token);
    }

    private String getApiToken() {
        return authManager.issueTokenForCurrentUser().getToken();
    }

    public List<DtsDeletion> findDeletions() {
        return ListUtils.emptyIfNull(dtsRegistryManager.loadAll())
                .stream()
                .map(this::findDeletions)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<DtsDeletion> findDeletions(final Long dtsId) {
        return findDeletions(dtsRegistryManager.loadById(dtsId));
    }

    private List<DtsDeletion> findDeletions(final DtsRegistry dts) {
        try {
            final List<DtsDeletion> deletions = findDeletions(getDtsClient(dts));
            return ListUtils.emptyIfNull(deletions).stream()
                    .peek(deletion -> deletion.setDtsId(dts.getId()))
                    .collect(Collectors.toList());
        } catch (DtsRequestException e) {
            log.warn("DTS #{} deletions listing has failed.", dts.getId());
            return Collections.emptyList();
        }
    }

    private List<DtsDeletion> findDeletions(final DtsClient client) {
        return DtsClient.executeRequest(client.findDeletions()).getPayload();
    }

    public DtsDeletion createDeletion(final Long dtsId, final CreateDtsDeletionRequest request) {
        return createDeletion(dtsRegistryManager.loadById(dtsId), request);
    }

    public DtsDeletion createDeletion(final DtsRegistry dts, final CreateDtsDeletionRequest request) {
        return createDeletion(getDtsClient(dts), request);
    }

    private DtsDeletion createDeletion(final DtsClient client, final CreateDtsDeletionRequest request) {
        return DtsClient.executeRequest(client.createDeletion(toDtsRequest(request))).getPayload();
    }

    private DtsCreateDeletionRequest toDtsRequest(final CreateDtsDeletionRequest request) {
        final DtsCreateDeletionRequest dtsRequest = new DtsCreateDeletionRequest();
        dtsRequest.setTarget(toStorageItem(request.getTarget()));
        dtsRequest.setScheduled(request.getScheduled());
        return dtsRequest;
    }

    private DtsTaskStorageItem toStorageItem(final CreateDtsDeletionRequestStorageItem item) {
        final DtsTaskStorageItem source = new DtsTaskStorageItem();
        source.setPath(item.getPath());
        source.setType(item.getType());
        return source;
    }
}
