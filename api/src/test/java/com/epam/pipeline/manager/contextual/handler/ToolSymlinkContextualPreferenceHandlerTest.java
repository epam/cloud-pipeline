package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.dao.tool.ToolDao;
import com.epam.pipeline.entity.contextual.ContextualPreference;
import com.epam.pipeline.entity.contextual.ContextualPreferenceExternalResource;
import com.epam.pipeline.entity.contextual.ContextualPreferenceLevel;
import com.epam.pipeline.entity.pipeline.Tool;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ToolSymlinkContextualPreferenceHandlerTest extends AbstractContextualPreferenceHandlerTest {

    private final ToolDao toolDao = mock(ToolDao.class);
    
    @Override
    ContextualPreferenceHandler handler() {
        return new ToolSymlinkContextualPreferenceHandler(toolDao, nextHandler);
    }

    @Override
    ContextualPreferenceHandler lastHandler() {
        return new ToolSymlinkContextualPreferenceHandler(toolDao);
    }

    @Override
    ContextualPreferenceLevel level() {
        return ContextualPreferenceLevel.TOOL;
    }
    
    @Test
    public void isValidShouldDelegateExecutionToTheNextHandlerIfToolDoesNotExist() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(toolDao.loadTool(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(null);
        when(nextHandler.isValid(eq(preference))).thenReturn(true);

        assertTrue(handler().isValid(preference));
        verify(nextHandler).isValid(eq(preference));
    }

    @Test
    public void isValidShouldDelegateExecutionToTheNextHandlerIfToolExistAndItIsNotSymlink() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(toolDao.loadTool(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(new Tool());
        when(nextHandler.isValid(eq(preference))).thenReturn(true);

        assertTrue(handler().isValid(preference));
        verify(nextHandler).isValid(eq(preference));
    }

    @Test
    public void isValidShouldReturnFalseIfToolExistAndItIsSymlink() {
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(toolDao.loadTool(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(symlink());
        when(nextHandler.isValid(eq(preference))).thenReturn(true);

        assertFalse(handler().isValid(preference));
        verify(nextHandler, times(0)).isValid(eq(preference));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfNoResourceSuitsCurrentHandler() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(notSuitableResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfThereIsToolResource() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(resource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(nextHandler.search(eq(SINGLE_NAME), eq(resources))).thenReturn(Optional.of(preference));

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resources));
    }

    @Test
    public void searchShouldDelegateExecutionToTheNextHandlerIfThereIsToolSymlinkResource() {
        final List<ContextualPreferenceExternalResource> resources = Collections.singletonList(resource);
        final List<ContextualPreferenceExternalResource> resolvedResources = Collections.singletonList(anotherResource);
        final ContextualPreference preference = new ContextualPreference(NAME, VALUE, resource);
        when(nextHandler.search(eq(SINGLE_NAME), eq(resolvedResources))).thenReturn(Optional.of(preference));
        when(toolDao.loadTool(eq(Long.valueOf(RESOURCE_ID)))).thenReturn(symlink());

        final Optional<ContextualPreference> searchedPreference = handler().search(SINGLE_NAME, resources);

        assertTrue(searchedPreference.isPresent());
        assertThat(searchedPreference.get(), is(preference));
        verify(nextHandler).search(eq(SINGLE_NAME), eq(resolvedResources));
    }

    private Tool symlink() {
        final Tool linkedResource = new Tool();
        linkedResource.setLink(Long.valueOf(ANOTHER_RESOURCE_ID));
        return linkedResource;
    }
}
