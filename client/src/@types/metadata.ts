export type MetadataEntityRef = {
  entityId: number;
  entityClass: string;
};

export type MetadataAttribute = {
  value?: string | number | boolean | null;
  type?: string;
};

export type MetadataEntity = {
  entityId: number;
  entityClass: string;
  id?: number;
  name?: string;
  parentId?: number;
  externalId?: string;
  classId?: number;
  className?: string;
  data?: Record<string, MetadataAttribute>;
};

export type MetadataEntityData = Record<string, MetadataAttribute>;

export type MetadataLoadResponseItem = {
  data?: MetadataEntityData;
  entity?: MetadataEntity;
  issuesCount?: number;
};

export type MetadataClass = {
  id?: number;
  name?: string;
  description?: string;
  parentId?: number;
  metadataEntityCount?: number;
};

export type MetadataFilter = {
  page?: number;
  pageSize?: number;
  folderId?: number;
  metadataClass?: string;
  metadataFields?: Record<string, unknown>;
};

export type MetadataKeyUpdate = {
  entity?: MetadataEntityRef;
  key?: string;
  value?: MetadataAttribute;
};

export type MetadataKeysBulkUpdateRequest = {
  entity: MetadataEntityRef;
  data: MetadataEntityData;
};
