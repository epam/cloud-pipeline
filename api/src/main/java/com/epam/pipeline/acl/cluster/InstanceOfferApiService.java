package com.epam.pipeline.acl.cluster;

import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class InstanceOfferApiService {

    private final InstanceOfferScheduler instanceOfferScheduler;

    @PreAuthorize(ADMIN_ONLY)
    public void updatePriceList(final Long id) {
        instanceOfferScheduler.updatePriceList(id);
    }
}
