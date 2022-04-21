package com.epam.pipeline.manager.dts;

import com.epam.pipeline.entity.dts.CreateDtsDeletionRequest;
import com.epam.pipeline.entity.dts.CreateDtsDeletionRequestStorageItem;
import com.epam.pipeline.entity.dts.CreateDtsTransferRequest;
import com.epam.pipeline.entity.dts.CreateDtsTransferRequestStorageItem;
import com.epam.pipeline.entity.dts.DtsCreateTransferRequest;
import com.epam.pipeline.entity.dts.DtsPipelineCredentials;
import com.epam.pipeline.entity.dts.DtsRegistry;
import com.epam.pipeline.entity.dts.DtsTaskStorageItem;
import com.epam.pipeline.entity.dts.DtsTransfer;
import com.epam.pipeline.exception.DtsRequestException;
import com.epam.pipeline.exception.SystemPreferenceNotSetException;
import com.epam.pipeline.manager.preference.AbstractSystemPreference;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DtsTransferManager {

    private final AuthManager authManager;
    private final DtsRegistryManager dtsRegistryManager;
    private final DtsDeletionManager dtsDeletionManager;
    private final DtsClientBuilder clientBuilder;
    private final PreferenceManager preferenceManager;

    public List<DtsTransfer> findTransfers() {
        return ListUtils.emptyIfNull(dtsRegistryManager.loadAll())
                .stream()
                .map(this::findTransfers)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<DtsTransfer> findTransfers(final Long dtsId) {
        return findTransfers(dtsRegistryManager.loadById(dtsId));
    }

    private List<DtsTransfer> findTransfers(final DtsRegistry dts) {
        try {
            final List<DtsTransfer> transfers = findTransfers(getDtsClient(dts));
            return ListUtils.emptyIfNull(transfers).stream()
                    .peek(transfer -> transfer.setDtsId(dts.getId()))
                    .collect(Collectors.toList());
        } catch (DtsRequestException e) {
            log.warn("DTS #{} transfers listing has failed.", dts.getId());
            return Collections.emptyList();
        }
    }

    private List<DtsTransfer> findTransfers(final DtsClient client) {
        return DtsClient.executeRequest(client.findTransfers()).getPayload();
    }

    private DtsClient getDtsClient(final DtsRegistry dts) {
        final String token = getApiToken();
        return clientBuilder.createDtsClient(dts.getUrl(), token);
    }

    public DtsTransfer createTransfer(final Long dtsId, final CreateDtsTransferRequest request) {
        return createTransfer(dtsRegistryManager.loadById(dtsId), request);
    }

    private DtsTransfer createTransfer(final DtsRegistry dts, final CreateDtsTransferRequest request) {
        final DtsClient client = getDtsClient(dts);
        final DtsTransfer transfer = createTransfer(client, request);
        createDeletionIfRequired(dts, request);
        return transfer;
    }

    private void createDeletionIfRequired(final DtsRegistry dts, final CreateDtsTransferRequest request) {
        Optional.ofNullable(request.getDeleteDestinationOn())
                .ifPresent(deleteDestinationOn -> dtsDeletionManager.createDeletion(dts.getId(),
                        new CreateDtsDeletionRequest(toDeletionItem(request.getDestination()), deleteDestinationOn)));
    }

    private CreateDtsDeletionRequestStorageItem toDeletionItem(final CreateDtsTransferRequestStorageItem item) {
        return new CreateDtsDeletionRequestStorageItem(item.getPath(), item.getType());
    }

    private DtsTransfer createTransfer(final DtsClient client, final CreateDtsTransferRequest request) {
        return DtsClient.executeRequest(client.createTransfer(toDtsRequest(request))).getPayload();
    }

    private DtsCreateTransferRequest toDtsRequest(final CreateDtsTransferRequest request) {
        final DtsPipelineCredentials credentials = getTransferStorageItemCredentials();
        final DtsCreateTransferRequest dtsRequest = new DtsCreateTransferRequest();
        dtsRequest.setSource(toStorageItem(request.getSource(), credentials));
        dtsRequest.setDestination(toStorageItem(request.getDestination(), credentials));
        return dtsRequest;
    }

    private DtsTaskStorageItem toStorageItem(final CreateDtsTransferRequestStorageItem item,
                                             final DtsPipelineCredentials credentials) {
        final DtsTaskStorageItem source = new DtsTaskStorageItem();
        source.setPath(item.getPath());
        source.setType(item.getType());
        source.setCredentials(credentials);
        return source;
    }

    private DtsPipelineCredentials getTransferStorageItemCredentials() {
        return new DtsPipelineCredentials(getApi(), getApiToken());
    }

    private String getApi() {
        return Optional.of(SystemPreferences.BASE_API_HOST_EXTERNAL)
                .map(AbstractSystemPreference::getKey)
                .map(preferenceManager::getStringPreference)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new SystemPreferenceNotSetException(SystemPreferences.BASE_API_HOST_EXTERNAL));
    }

    private String getApiToken() {
        return authManager.issueTokenForCurrentUser().getToken();
    }

}
