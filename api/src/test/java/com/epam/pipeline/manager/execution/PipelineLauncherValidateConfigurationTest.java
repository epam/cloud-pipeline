package com.epam.pipeline.manager.execution;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.run.RunAssignPolicy;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.cluster.KubernetesConstants;
import com.epam.pipeline.manager.security.AuthManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnusedPrivateField")
public class PipelineLauncherValidateConfigurationTest {

    public static final String SOME_LABEL = "some-label";
    public static final String VALUE = "true";
    public static final String KUBE_SERVICE_ACCOUNT = "some-account";
    public static final String RUN_ID_VALUE = "1";

    @Mock
    private AuthManager authManager;
    @Mock
    private MessageHelper messageHelper;

    @InjectMocks
    private PipelineLauncher pipelineLauncher;

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
        pipelineLauncher.validateLaunchConfiguration(configuration);
    }

    @Test
    public void checkRunLaunchWithKubeServiceAccIsForbiddenForSimpleUser() {
        Mockito.doReturn(PipelineUser.builder().admin(false).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setKubeServiceAccount(KUBE_SERVICE_ACCOUNT);
        assertThrows(IllegalStateException.class, () -> pipelineLauncher.validateLaunchConfiguration(configuration));
    }

    @Test
    public void checkRunLaunchWithAdvancedRunAssignPolicyIsForbiddenForSimpleUser() {
        Mockito.doReturn(PipelineUser.builder().admin(false).build()).when(authManager).getCurrentUser();
        final PipelineConfiguration configuration = new PipelineConfiguration();
        configuration.setPodAssignPolicy(
            RunAssignPolicy.builder()
                .selector(RunAssignPolicy.PodAssignSelector.builder().label(SOME_LABEL).value(VALUE).build()).build()
        );
        assertThrows(IllegalStateException.class, () -> pipelineLauncher.validateLaunchConfiguration(configuration));
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
        pipelineLauncher.validateLaunchConfiguration(configuration);
    }
}