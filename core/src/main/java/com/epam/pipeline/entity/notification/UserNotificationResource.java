package com.epam.pipeline.entity.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@Builder
@Table(name = "user_notification_resource", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "notification_id", referencedColumnName = "id")
    private UserNotification notification;
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
