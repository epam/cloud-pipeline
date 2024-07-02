package com.epam.pipeline.dto.datastorage.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Describe exception to {@link StorageLifecycleRule}.
 * When Rule is being evaluated, this prolongations should be checked also.

 * f.e. If files should be transferred to another Storage Class, regarding a {@link StorageLifecycleRule}
 * but this rule have a prolongation and {@code path} matches - we should calculate eligibility for transferring
 * with respect to prolongation object.
 * */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageLifecycleRuleProlongation {
    Long id;
    Long userId;
    String path;
    LocalDateTime prolongedDate;
    Long days;
}
