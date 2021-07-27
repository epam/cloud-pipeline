package com.epam.pipeline.entity.utils;

import com.epam.pipeline.entity.pipeline.TaskStatus;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.AttributeConverter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RunStatusesListConverter implements AttributeConverter<List<TaskStatus>, String> {

    @Override
    public String convertToDatabaseColumn(List<TaskStatus> attribute) {
        return attribute.stream()
                .map(TaskStatus::getId)
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    @Override
    public List<TaskStatus> convertToEntityAttribute(String dbData) {
        return Arrays.stream(dbData.split(","))
                .filter(StringUtils::isNotBlank)
                .map(Long::parseLong)
                .map(TaskStatus::getById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
