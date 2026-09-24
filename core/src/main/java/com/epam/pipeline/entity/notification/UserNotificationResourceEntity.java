package com.epam.pipeline.entity.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@Table(name = "user_notification_resource", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "notification_id", referencedColumnName = "id")
    private UserNotificationEntity notification;
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_class")
    private NotificationEntityClass entityClass;
    @Column(name = "entity_id")
    private Long entityId;
    @Column(name = "storage_path")
    private String storagePath;
    @Column(name = "storage_rule_id")
    private Long storageRuleId;
}
