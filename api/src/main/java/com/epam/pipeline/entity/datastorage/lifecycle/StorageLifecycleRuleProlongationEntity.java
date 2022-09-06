package com.epam.pipeline.entity.datastorage.lifecycle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Builder
@Getter
@Setter
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

    private Long userId;

    private String path;
    private LocalDateTime prolongedDate;
    private Long days;
}
