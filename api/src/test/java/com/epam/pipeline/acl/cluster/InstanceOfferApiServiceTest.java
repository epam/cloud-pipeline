package com.epam.pipeline.acl.cluster;

import com.epam.pipeline.manager.cluster.InstanceOfferScheduler;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.mockito.Mockito.verify;

public class InstanceOfferApiServiceTest extends AbstractAclTest {

    @Autowired
    private InstanceOfferApiService instanceOfferApiService;

    @Autowired
    private InstanceOfferScheduler mockInstanceOfferScheduler;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdatePriceListForAdmin() {
        instanceOfferApiService.updatePriceList(ID);

        verify(mockInstanceOfferScheduler).updatePriceList(ID);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailUpdatingPriceListWithoutPermission() {
        instanceOfferApiService.updatePriceList(ID);
    }
}
