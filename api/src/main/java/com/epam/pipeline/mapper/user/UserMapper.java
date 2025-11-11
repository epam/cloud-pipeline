package com.epam.pipeline.mapper.user;

import com.epam.pipeline.entity.user.PipelineUser;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public interface UserMapper {

    static Map<String, Object> map(final PipelineUser user, final Map<Long, String> userStorages) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", user.getUserName());
        parameters.put("email", user.getEmail());
        if (Objects.nonNull(user.getDefaultStorageId())) {
            parameters.put("storage_name", userStorages.get(user.getDefaultStorageId()));
        }
        parameters.put("registration_date", user.getRegistrationDate());
        if (Objects.nonNull(user.getLastLoginDate())) {
            parameters.put("last_login_date", user.getLastLoginDate());
        }
        if (Objects.nonNull(user.getBlockDate())) {
            parameters.put("block_date", user.getBlockDate());
        }
        if (Objects.nonNull(user.getExternalBlockDate())) {
            parameters.put("external_block_date", user.getExternalBlockDate());
        }
        return parameters;
    }
}
