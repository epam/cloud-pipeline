package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.execution.LaunchCommandTemplateForImagePattern;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.*;
import java.util.function.Predicate;

public final class PodLaunchCommandHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineLauncher.class);
    private static final String ALL_KEY_WORK = "all";
    private static final String ALL_IMAGE_REGEXP = ".*";
    private static final String DOT = ".";
    private static final String ESCAPED_DOT = "\\.";
    private static final String STAR_SIGN = "*";
    public static final String LAUNCH_COMMAND_TAG = "launch_command";

    private PodLaunchCommandHelper() {}

    static String pickLaunchCommandTemplate(final List<LaunchCommandTemplateForImagePattern> commandsByImage,
                                            final String dockerImage) {
        final Pair<LaunchCommandTemplateForImagePattern, List<LaunchCommandTemplateForImagePattern>>
                commandsByImageWithDefault =
                filtered(
                        ListUtils.emptyIfNull(commandsByImage),
                        (e) -> !e.getImage().equals(STAR_SIGN) && !e.getImage().equals(ALL_KEY_WORK)
                );
        final Optional<LaunchCommandTemplateForImagePattern> matchedCommandOp =
                commandsByImageWithDefault.getValue().stream().filter(imageAndCommand -> {
                    final String preparedImageRegexp = imageAndCommand.getImage().equals(ALL_KEY_WORK)
                            ? ALL_IMAGE_REGEXP
                            : ALL_IMAGE_REGEXP + imageAndCommand.getImage()
                            .replace(DOT, ESCAPED_DOT)
                            .replace(STAR_SIGN, ALL_IMAGE_REGEXP);
                    return dockerImage.matches(preparedImageRegexp);
                }).findFirst();
        String effectiveLaunchCommand = Optional.ofNullable(commandsByImageWithDefault.getKey())
                .map(LaunchCommandTemplateForImagePattern::getCommand).orElse(null);
        if (matchedCommandOp.isPresent()) {
            LOGGER.debug("Matched launch command by image: {} will be used.", matchedCommandOp.get().getImage());
            effectiveLaunchCommand = matchedCommandOp.get().getCommand();
        } else {
            LOGGER.debug(
                    "No matching launch command was found for image {}. " +
                            "Default one will be used.", dockerImage);
        }
        return effectiveLaunchCommand;
    }

    static String evaluateLaunchCommandTemplate(final String commandTemplate, final Map<String, String> args) {
        final StringWriter commandWriter = new StringWriter();
        final VelocityContext velocityContext = new VelocityContext();
        args.forEach(velocityContext::put);
        Velocity.evaluate(
                velocityContext, commandWriter,
                LAUNCH_COMMAND_TAG + commandTemplate.hashCode(), commandTemplate
        );
        return commandWriter.toString();
    }

    private static Pair<LaunchCommandTemplateForImagePattern, List<LaunchCommandTemplateForImagePattern>> filtered(
            final List<LaunchCommandTemplateForImagePattern> list,
            final Predicate<LaunchCommandTemplateForImagePattern> filter) {
        LaunchCommandTemplateForImagePattern filteredOut = null;
        final List<LaunchCommandTemplateForImagePattern> filtered = new ArrayList<>();
        for (LaunchCommandTemplateForImagePattern template : list) {
            if (!filter.test(template)) {
                filteredOut = template;
            } else {
                filtered.add(template);
            }
        }
        return Pair.of(filteredOut, filtered);
    }
}
