package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.manager.docker.DockerRegistryManager;
import com.epam.pipeline.test.creator.CommonCreatorConstants;
import com.epam.pipeline.test.creator.docker.DockerCreatorUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class ToolManagerUnitTest {

    private static final String COLON = ":";
    private static final String SLASH = "/";
    private static final Long REGISTRY_ID = CommonCreatorConstants.ID;
    private static final String REGISTRY = "registry:443";
    private static final Long TOOL_ID = CommonCreatorConstants.ID;
    private static final String TOOL_IMAGE = "library/image";
    private static final Long SYMLINK_ID = CommonCreatorConstants.ID_2;
    private static final String SYMLINK_IMAGE = "personal/symlink";
    private static final String LATEST_TAG = "latest";
    private static final String SOME_TAG = "tag";

    private final ToolManager manager = new ToolManager();
    private final ToolDao toolDao = mock(ToolDao.class);
    private final DockerRegistryManager dockerRegistryManager = mock(DockerRegistryManager.class);
    private final MessageHelper messageHelper = mock(MessageHelper.class);

    @Before
    public void setUp() {
        Whitebox.setInternalState(manager, "toolDao", toolDao);
        Whitebox.setInternalState(manager, "dockerRegistryManager", dockerRegistryManager);
        Whitebox.setInternalState(manager, "messageHelper", messageHelper);
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithLatestTag() {
        mockTool(getTool());
        mockRegistry(getRegistry());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithSomeTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnToolWithFullDockerImageWithLatestTagByDefault() {
        mockTool(getTool());
        mockRegistry(getRegistry());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + TOOL_IMAGE);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithLatestTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE + COLON + LATEST_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithSomeTag() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE + COLON + SOME_TAG);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + SOME_TAG));
    }

    @Test
    public void resolveSymlinksShouldReturnSymlinkedToolWithFullDockerImageWithLatestTagByDefault() {
        mockRegistry(getRegistry());
        mockTool(getTool());
        mockTool(getSymlink());

        final Tool resolvedTool = manager.resolveSymlinks(REGISTRY + SLASH + SYMLINK_IMAGE);
        assertThat(resolvedTool.getImage(), is(REGISTRY + SLASH + TOOL_IMAGE + COLON + LATEST_TAG));
    }

    private Tool getTool() {
        final Tool tool = DockerCreatorUtils.getTool();
        tool.setId(TOOL_ID);
        tool.setImage(TOOL_IMAGE);
        tool.setRegistry(REGISTRY);
        tool.setRegistryId(REGISTRY_ID);
        return tool;
    }

    private Tool getSymlink() {
        final Tool symlink = DockerCreatorUtils.getTool();
        symlink.setId(SYMLINK_ID);
        symlink.setImage(SYMLINK_IMAGE);
        symlink.setRegistry(REGISTRY);
        symlink.setRegistryId(REGISTRY_ID);
        symlink.setLink(TOOL_ID);
        return symlink;
    }

    private DockerRegistry getRegistry() {
        final DockerRegistry registry = DockerCreatorUtils.getDockerRegistry();
        registry.setId(REGISTRY_ID);
        return registry;
    }

    private void mockTool(final Tool tool) {
        doReturn(tool).when(toolDao).loadTool(tool.getRegistryId(), tool.getImage());
        doReturn(tool).when(toolDao).loadTool(tool.getId());
    }

    private void mockRegistry(final DockerRegistry registry) {
        doReturn(registry).when(dockerRegistryManager).loadByNameOrId(REGISTRY);
    }
}
