package com.epam.pipeline.acl.pipeline;


import com.epam.pipeline.entity.configuration.ConfigurationEntry;
import com.epam.pipeline.entity.configuration.PipelineConfiguration;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.exception.git.GitClientException;
import com.epam.pipeline.manager.pipeline.PipelineVersionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.configuration.ConfigurationCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrowsChecked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class PipelineConfigApiServiceTest extends AbstractAclTest {

    private final ConfigurationEntry configurationEntry = ConfigurationCreatorUtils.getConfigurationEntry();
    private final Pipeline pipeline = PipelineCreatorUtils.getPipeline(ANOTHER_SIMPLE_USER);
    private final PipelineConfiguration pipelineConfiguration = ConfigurationCreatorUtils.getPipelineConfiguration();
    private final List<ConfigurationEntry> configurationEntries = Collections.singletonList(configurationEntry);

    @Autowired
    private PipelineConfigApiService configApiService;

    @Autowired
    private PipelineVersionManager mockVersionManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadConfigurationsForAdmin() throws GitClientException {
        doReturn(configurationEntries).when(mockVersionManager).loadConfigurationsFromScript(ID, TEST_STRING);

        assertThat(configApiService.loadConfigurations(ID, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadConfigurationsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(configurationEntries).when(mockVersionManager).loadConfigurationsFromScript(ID, TEST_STRING);

        assertThat(configApiService.loadConfigurations(ID, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadConfigurationsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(configurationEntries).when(mockVersionManager).loadConfigurationsFromScript(ID, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                configApiService.loadConfigurations(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAddConfigurationForAdmin() throws GitClientException {
        doReturn(configurationEntries).when(mockVersionManager).addConfiguration(ID, configurationEntry);

        assertThat(configApiService.addConfiguration(ID, configurationEntry)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAddConfigurationsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(configurationEntries).when(mockVersionManager).addConfiguration(ID, configurationEntry);

        assertThat(configApiService.addConfiguration(ID, configurationEntry)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyAddConfigurationsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(configurationEntries).when(mockVersionManager).addConfiguration(ID, configurationEntry);

        assertThrowsChecked(AccessDeniedException.class, () ->
                configApiService.addConfiguration(ID, configurationEntry));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteConfigurationForAdmin() throws GitClientException {
        doReturn(configurationEntries).when(mockVersionManager).deleteConfiguration(ID, TEST_STRING);

        assertThat(configApiService.deleteConfiguration(ID, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteConfigurationsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(configurationEntries).when(mockVersionManager).deleteConfiguration(ID, TEST_STRING);

        assertThat(configApiService.deleteConfiguration(ID, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyDeleteConfigurationsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(configurationEntries).when(mockVersionManager).deleteConfiguration(ID, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                configApiService.deleteConfiguration(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadParametersFromScriptForAdmin() throws GitClientException {
        doReturn(pipelineConfiguration).when(mockVersionManager).loadParametersFromScript(ID, TEST_STRING, TEST_STRING);

        assertThat(configApiService.loadParametersFromScript(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(pipelineConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadParametersFromScriptWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(pipelineConfiguration).when(mockVersionManager).loadParametersFromScript(ID, TEST_STRING, TEST_STRING);

        assertThat(configApiService.loadParametersFromScript(ID, TEST_STRING, TEST_STRING))
                .isEqualTo(pipelineConfiguration);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyLoadParametersFromScriptWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(pipelineConfiguration).when(mockVersionManager).loadParametersFromScript(ID, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                configApiService.loadParametersFromScript(ID, TEST_STRING, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldRenameConfigurationForAdmin() throws GitClientException {
        doReturn(configurationEntries).when(mockVersionManager).renameConfiguration(ID, TEST_STRING, TEST_STRING);

        assertThat(configApiService.renameConfiguration(ID, TEST_STRING, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldRenameConfigurationsWhenPermissionIsGranted() throws GitClientException {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(configurationEntries).when(mockVersionManager).renameConfiguration(ID, TEST_STRING, TEST_STRING);

        assertThat(configApiService.renameConfiguration(ID, TEST_STRING, TEST_STRING)).isEqualTo(configurationEntries);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRenameConfigurationsWhenPermissionIsNotGranted() throws GitClientException {
        initAclEntity(pipeline);
        doReturn(configurationEntries).when(mockVersionManager).renameConfiguration(ID, TEST_STRING, TEST_STRING);

        assertThrowsChecked(AccessDeniedException.class, () ->
                configApiService.renameConfiguration(ID, TEST_STRING, TEST_STRING));
    }
}
