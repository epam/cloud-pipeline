package com.epam.pipeline.manager.contextual.handler;

import com.epam.pipeline.entity.contextual.ContextualPreference;
import org.apache.commons.lang3.BooleanUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;

public class BooleanContextualPreferenceReducer implements ContextualPreferenceReducer {

    private final BinaryOperator<Boolean> reducer = (left, right) -> left || right;

    @Override
    public Optional<ContextualPreference> reduce(final List<ContextualPreference> preferences) {
        if (preferences.isEmpty()) {
            return Optional.empty();
        }
        final boolean value = preferences.stream()
                .map(ContextualPreference::getValue)
                .map(BooleanUtils::toBoolean)
                .reduce(false, reducer);
        return Optional.of(preferences.get(0)
                .withValue(BooleanUtils.toString(value, "true", "false"))
                .withCreatedDate(null)
                .withResource(null));
    }
}
