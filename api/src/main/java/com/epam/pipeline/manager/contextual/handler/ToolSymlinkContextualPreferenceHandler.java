package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.Tool;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToolSymlinkContextualPreferenceHandler extends AbstractContextualPreferenceHandler {

    private final ToolDao toolDao;

    public ToolSymlinkContextualPreferenceHandler(final ToolDao toolDao,
                                                  final ContextualPreferenceHandler nextHandler) {
        super(ContextualPreferenceLevel.TOOL, nextHandler);
        this.toolDao = toolDao;
    }

    public ToolSymlinkContextualPreferenceHandler(final ToolDao toolDao) {
        super(ContextualPreferenceLevel.TOOL, null);
        this.toolDao = toolDao;
    }

    @Override
    boolean externalEntityExists(final ContextualPreference preference) {
        return !isSymlinkResource(preference) && validateUsingNextHandler(preference).orElse(false);
    }

    private boolean isSymlinkResource(final ContextualPreference preference) {
        return loadResource(preference.getResource()).filter(Tool::isSymlink).isPresent();
    }

    @Override
    public Optional<ContextualPreference> search(final List<String> preferences, 
                                                 final List<ContextualPreferenceExternalResource> resources) {
        return searchUsingNextHandler(preferences, resolveSymlinks(resources));
    }

    private List<ContextualPreferenceExternalResource> resolveSymlinks(
            final List<ContextualPreferenceExternalResource> resources) {
        return resources.stream().map(this::resolveSymlinks).collect(Collectors.toList());
    }

    private ContextualPreferenceExternalResource resolveSymlinks(final ContextualPreferenceExternalResource resource) {
        return loadResource(resource)
            .filter(Tool::isSymlink)
            .map(Tool::getLink)
            .map(Object::toString)
            .map(id -> new ContextualPreferenceExternalResource(level, id))
            .orElse(resource);
    }

    private Optional<Tool> loadResource(final ContextualPreferenceExternalResource resource) {
        return Optional.of(resource)
                .filter(r -> r.getLevel() == level)
                .map(ContextualPreferenceExternalResource::getResourceId)
                .map(Long::valueOf)
                .map(toolDao::loadTool);
    }
}
