package com.epam.pipeline.entity.datastorage.security;

import com.epam.pipeline.dto.datastorage.security.StoragePermissionPathType;
import com.epam.pipeline.dto.datastorage.security.StoragePermissionSidType;
import com.epam.pipeline.entity.utils.TimestampConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder(toBuilder = true)
@Table(name = "datastorage_permission", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
@IdClass(StoragePermissionEntityId.class)
@TypeDefs({
        @TypeDef(name = "StoragePermissionPathTypeUserType",
                typeClass = StoragePermissionPathTypeUserType.class),
        @TypeDef(name = "StoragePermissionSidTypeUserType",
                typeClass = StoragePermissionSidTypeUserType.class),
})
public class StoragePermissionEntity {

    @Id
    private Long datastorageRootId;

    @Id
    private String datastoragePath;

    @Id
    @Type(type = "StoragePermissionPathTypeUserType")
    private StoragePermissionPathType datastorageType;

    @Id
    private String sidName;

    @Id
    @Type(type = "StoragePermissionSidTypeUserType")
    private StoragePermissionSidType sidType;

    private int mask;

    @Convert(converter = TimestampConverter.class)
    private LocalDateTime created;

}
