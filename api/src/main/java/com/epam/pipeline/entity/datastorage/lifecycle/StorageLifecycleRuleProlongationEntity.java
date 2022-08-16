package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "datastorage_lifecycle_rule_prolongation", schema = "pipeline")
public class StorageLifecycleRuleProlongationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "rule_id", referencedColumnName = "id")
    private StorageLifecycleRuleEntity lifecycleRule;

    String path;
    LocalDateTime prolongedDate;
    Long days;
}
