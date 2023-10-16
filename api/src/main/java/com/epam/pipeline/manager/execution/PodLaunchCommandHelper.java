package com.epam.pipeline.manager.execution;

import com.epam.pipeline.entity.execution.OSSpecificLaunchCommandTemplate;
import com.epam.pipeline.entity.scan.ToolOSVersion;
import com.epam.pipeline.manager.pipeline.ToolUtils;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
public final class PodLaunchCommandHelper {

    private static final String ALL_KEY_WORK = "all";
    private static final String STAR_SIGN = "*";
    public static final String LAUNCH_COMMAND_TAG = "launch_command";

    private PodLaunchCommandHelper() {
    }

    static OSSpecificLaunchCommandTemplate pickLaunchCommandTemplate(
            final List<OSSpecificLaunchCommandTemplate> commandsByImage,
            final ToolOSVersion osVersion,
            final String dockerImage) {

        final Pair<OSSpecificLaunchCommandTemplate, List<OSSpecificLaunchCommandTemplate>>
                commandsByImageWithDefault =
                filtered(
                    ListUtils.emptyIfNull(commandsByImage),
                    (e) -> !e.getOs().equals(STAR_SIGN) && !e.getOs().equals(ALL_KEY_WORK)
                );

        final OSSpecificLaunchCommandTemplate dockerMatch =
                matchByDocker(commandsByImageWithDefault.getValue(), dockerImage);

        if (Objects.nonNull(dockerMatch)) {
            log.debug("Matched launch command by docker {}.", dockerImage);
            return dockerMatch;
        }

        if (osVersion != null) {
            final Optional<OSSpecificLaunchCommandTemplate> matchedCommandOp =
                    commandsByImageWithDefault.getValue().stream()
                            .filter(imageAndCommand ->
                                    osVersion.isMatched(imageAndCommand.getOs())).findFirst();
            if (matchedCommandOp.isPresent()) {
                log.debug("Matched launch command by image: {} will be used.",
                        matchedCommandOp.get().getOs());
                return matchedCommandOp.get();
            }
        }

        log.debug("No matching launch command was found for image {}. " +
                "Default one will be used.", osVersion);
        return commandsByImageWithDefault.getKey();
    }

    static OSSpecificLaunchCommandTemplate matchByDocker(
            final List<OSSpecificLaunchCommandTemplate> templates,
            final String dockerImage) {
        return templates.stream()
                .filter(template -> matchImage(template.getDocker(), dockerImage))
                .findFirst().orElse(null);
    }

    static boolean matchImage(final String template, final String docker) {
        if (StringUtils.isEmpty(template)) {
            return false;
        }
        final DockerImage templateImage = new DockerImage(template);
        final DockerImage runImage = new DockerImage(docker);

        if (!templateImage.getImage().equals(runImage.getImage())) {
            return false;
        }

        if (StringUtils.isEmpty(templateImage.getTag())) {
            return true;
        }

        return templateImage.getTag().equals(runImage.getTag());
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

    @Value
    static class DockerImage {
        String image;
        String tag;

        DockerImage(final String dockerImage) {
            final String trimmed = ToolUtils.getImageWithoutRepository(dockerImage).orElse(dockerImage);
            if (trimmed.contains(ToolUtils.TAG_DELIMITER)) {
                final String[] chunks = trimmed.split(ToolUtils.TAG_DELIMITER);
                image = chunks[0];
                tag = chunks[1];
            } else {
                image = trimmed;
                tag = null;
            }
        }
    }
}
