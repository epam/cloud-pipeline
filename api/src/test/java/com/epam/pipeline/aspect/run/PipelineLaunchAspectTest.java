package com.epam.pipeline.aspect.run;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class PipelineLaunchAspectTest {

    public static final String SOME_LABEL = "some-label";
    public static final String VALUE = "true";
    public static final String KUBE_SERVICE_ACCOUNT = "some-account";
    public static final String RUN_ID_VALUE = "1";

    private final AuthManager authManager = Mockito.mock(AuthManager.class);
    private final MessageHelper messageHelper = Mockito.mock(MessageHelper.class);
    private final PipelineLaunchAspect aspect = new PipelineLaunchAspect(authManager, messageHelper);

    @Before
    public void setUp() {
        Mockito.doReturn("").when(messageHelper).getMessage(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void checkRunLaunchIsNotForbiddenForAdmin() {
        Mockito.doReturn(PipelineUser.builder().admin(true).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setPodAssignPolicy(
                RunAssignPolicy.builder()
                        .selector(
                                RunAssignPolicy.PodAssignSelector.builder()
                                        .label(SOME_LABEL)
                                        .value(VALUE).build())
                        .build()
        );
        configuration.setKubeServiceAccount(KUBE_SERVICE_ACCOUNT);
        aspect.checkRunLaunchIsNotForbidden(null, configuration);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkRunLaunchWithKubeServiceAccIsForbiddenForSimpleUser() {
        Mockito.doReturn(PipelineUser.builder().admin(false).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setKubeServiceAccount(KUBE_SERVICE_ACCOUNT);
        aspect.checkRunLaunchIsNotForbidden(null, configuration);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkRunLaunchWithAdvancedRunAssignPolicyIsForbiddenForSimpleUser() {
        Mockito.doReturn(PipelineUser.builder().admin(false).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setPodAssignPolicy(
                RunAssignPolicy.builder()
                        .selector(
                                RunAssignPolicy.PodAssignSelector.builder()
                                        .label(SOME_LABEL)
                                        .value(VALUE).build())
                        .build()
        );
        aspect.checkRunLaunchIsNotForbidden(null, configuration);
    }

    @Test
    public void checkRunLaunchWithSimpleRunAssignPolicyIsAllowedForSimpleUser() {
        Mockito.doReturn(PipelineUser.builder().admin(false).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setPodAssignPolicy(
                RunAssignPolicy.builder()
                        .selector(
                                RunAssignPolicy.PodAssignSelector.builder()
                                        .label(KubernetesConstants.RUN_ID_LABEL)
                                        .value(RUN_ID_VALUE).build())
                        .build()
        );
        aspect.checkRunLaunchIsNotForbidden(null, configuration);
    }
}
