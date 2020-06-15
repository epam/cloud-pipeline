package com.epam.pipeline.dao.contextual;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.docker.DockerRegistryDao;
import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.dao.tool.ToolGroupDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.DockerRegistry;
import com.epam.pipeline.entity.pipeline.Tool;
import com.epam.pipeline.entity.pipeline.ToolGroup;
import com.epam.pipeline.entity.preference.PreferenceType;
import com.epam.pipeline.entity.utils.DateUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ToolSymlinkContextualPreferenceDaoTest  extends AbstractSpringTest {
    
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private static final String TEST_USER = "user";
    private static final String TEST_SOURCE_IMAGE = "library/image";
    private static final String TEST_SYMLINK_IMAGE = "user/image";
    private static final String TEST_CPU = "500m";
    private static final String TEST_RAM = "1Gi";
    private static final String TEST_REPO = "repository";
    private static final String TOOL_GROUP_NAME = "library";
    private static final String USER_GROUP_NAME = "user";

    private DockerRegistry firstRegistry;
    private DockerRegistry secondRegistry;
    private ToolGroup library;
    private ToolGroup firstUserGroup;
    private ToolGroup secondUserGroup;
    private Tool tool;
    private Tool symlink;
    private Tool secondSymlink;

    @Autowired
    private ToolDao toolDao;
    @Autowired
    private DockerRegistryDao registryDao;
    @Autowired
    private ToolGroupDao toolGroupDao;
    @Autowired
    private ContextualPreferenceDao contextualPreferenceDao;

    @Before
    public void setUp() throws Exception {
        firstRegistry = new DockerRegistry();
        firstRegistry.setPath(TEST_REPO);
        firstRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(firstRegistry);

        library = new ToolGroup();
        library.setName(TOOL_GROUP_NAME);
        library.setRegistryId(firstRegistry.getId());
        library.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(library);

        firstUserGroup = new ToolGroup();
        firstUserGroup.setName(USER_GROUP_NAME);
        firstUserGroup.setRegistryId(firstRegistry.getId());
        firstUserGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(firstUserGroup);

        tool = new Tool();
        tool.setImage(TEST_SOURCE_IMAGE);
        tool.setRam(TEST_RAM);
        tool.setCpu(TEST_CPU);
        tool.setOwner(TEST_USER);
        tool.setRegistryId(firstRegistry.getId());
        tool.setToolGroupId(library.getId());
        toolDao.createTool(tool);

        symlink = new Tool();
        symlink.setImage(TEST_SYMLINK_IMAGE);
        symlink.setRam(TEST_RAM);
        symlink.setCpu(TEST_CPU);
        symlink.setOwner(TEST_USER);
        symlink.setRegistryId(firstRegistry.getId());
        symlink.setToolGroupId(firstUserGroup.getId());
        symlink.setLink(tool.getId());
        toolDao.createTool(symlink);

        secondRegistry = new DockerRegistry();
        secondRegistry.setPath(TEST_REPO);
        secondRegistry.setOwner(TEST_USER);
        registryDao.createDockerRegistry(secondRegistry);

        secondUserGroup = new ToolGroup();
        secondUserGroup.setName(TOOL_GROUP_NAME);
        secondUserGroup.setRegistryId(secondRegistry.getId());
        secondUserGroup.setOwner(TEST_USER);
        toolGroupDao.createToolGroup(secondUserGroup);

        secondSymlink = new Tool();
        secondSymlink.setImage(TEST_SYMLINK_IMAGE);
        secondSymlink.setRam(TEST_RAM);
        secondSymlink.setCpu(TEST_CPU);
        secondSymlink.setOwner(TEST_USER);
        secondSymlink.setRegistryId(secondRegistry.getId());
        secondSymlink.setToolGroupId(secondUserGroup.getId());
        secondSymlink.setLink(tool.getId());
        toolDao.createTool(secondSymlink);
    }

    @Test
    @Transactional
    public void testLoadForTool() {
        final ContextualPreference expectedPreference = preference(tool);
        contextualPreferenceDao.upsert(expectedPreference);

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource(tool));
        
        assertTrue(loadedPreference.isPresent());
        final ContextualPreference actualPreference = loadedPreference.get();
        assertThat(actualPreference, is(expectedPreference));
    }

    @Test
    @Transactional
    public void testLoadForSymlink() {
        final ContextualPreference expectedPreference = preference(tool);
        contextualPreferenceDao.upsert(expectedPreference);

        final Optional<ContextualPreference> loadedPreference = contextualPreferenceDao.load(NAME, resource(symlink));
        
        assertTrue(loadedPreference.isPresent());
        final ContextualPreference actualPreference = loadedPreference.get();
        assertThat(actualPreference, is(expectedPreference));
    }

    @Test
    @Transactional
    public void testLoadForNonExistingTool() {
        Assert.assertFalse(contextualPreferenceDao.load(NAME, resource(tool)).isPresent());
        Assert.assertFalse(contextualPreferenceDao.load(NAME, resource(symlink)).isPresent());
    }

    private ContextualPreference preference(final Tool tool) {
        return preference(resource(tool));
    }

    private ContextualPreference preference(final ContextualPreferenceExternalResource resource) {
        return new ContextualPreference(NAME, VALUE, PreferenceType.STRING, DateUtils.now(), resource);
    }

    private ContextualPreferenceExternalResource resource(final Tool tool) {
        return new ContextualPreferenceExternalResource(ContextualPreferenceLevel.TOOL, tool.getId().toString());
    }
}
