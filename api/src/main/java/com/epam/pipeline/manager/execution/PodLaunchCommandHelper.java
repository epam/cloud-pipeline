package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class PodLaunchCommandHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineLauncher.class);
    private static final String ALL_KEY_WORK = "all";
    private static final String STAR_SIGN = "*";
    public static final String LAUNCH_COMMAND_TAG = "launch_command";

    private PodLaunchCommandHelper() {}

    static String pickLaunchCommandTemplate(final List<OSSpecificLaunchCommandTemplate> commandsByImage,
                                            final ToolOSVersion osVersion) {
        final Pair<OSSpecificLaunchCommandTemplate, List<OSSpecificLaunchCommandTemplate>>
                commandsByImageWithDefault =
                filtered(
                    ListUtils.emptyIfNull(commandsByImage),
                    (e) -> !e.getOs().equals(STAR_SIGN) && !e.getOs().equals(ALL_KEY_WORK)
                );
        String effectiveLaunchCommand = Optional.ofNullable(commandsByImageWithDefault.getKey())
                .map(OSSpecificLaunchCommandTemplate::getCommand).orElse(null);
        if (osVersion != null) {
            final Optional<OSSpecificLaunchCommandTemplate> matchedCommandOp =
                    commandsByImageWithDefault.getValue().stream()
                            .filter(imageAndCommand ->
                                    osVersion.isMatched(imageAndCommand.getOs())).findFirst();
            if (matchedCommandOp.isPresent()) {
                LOGGER.debug("Matched launch command by image: {} will be used.",
                        matchedCommandOp.get().getOs());
                effectiveLaunchCommand = matchedCommandOp.get().getCommand();
            } else {
                LOGGER.debug(
                        "No matching launch command was found for image {}. " +
                                "Default one will be used.", osVersion);
            }
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

    private static Pair<OSSpecificLaunchCommandTemplate, List<OSSpecificLaunchCommandTemplate>> filtered(
            final List<OSSpecificLaunchCommandTemplate> list,
            final Predicate<OSSpecificLaunchCommandTemplate> filter) {
        OSSpecificLaunchCommandTemplate filteredOut = null;
        final List<OSSpecificLaunchCommandTemplate> filtered = new ArrayList<>();
        for (OSSpecificLaunchCommandTemplate template : list) {
            if (!filter.test(template)) {
                filteredOut = template;
            } else {
                filtered.add(template);
            }
        }
        return Pair.of(filteredOut, filtered);
    }
}
