package com.epam.pipeline.entity.cloud;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents status of cloud provider operation like start instance and etc.
 * */
@Builder
@Getter
@Setter
public class CloudInstanceOperationResult {

    private Status status;
    private String message;


    public enum Status {
        OK, ERROR
    }
}
