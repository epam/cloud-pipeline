import type {CSSProperties, ReactNode} from 'react';
import {CommonProps} from '../../@types/common.ts';
import {MetadataEntityData, MetadataEntityRef} from '../../@types/metadata.ts';

export type MetadataItem = {
  key: string;
  value?: string;
  type?: string;
  index: number;
};

export type MetadataAddKeyState = {
  key: string;
  value: string;
  secret: boolean;
};

export type MetadataEditField = 'key' | 'value';

export const ApplyChanges = {
  callback: 'callback',
  inline: 'inline',
} as const;

export type ApplyChangesMode = (typeof ApplyChanges)[keyof typeof ApplyChanges];

export type MetadataProps = CommonProps & {
  entity?: MetadataEntityRef;
  readOnly?: boolean;
  hideMetadataTags?: boolean;
  entityName?: string;
  entityClass?: string;
  entityId?: number | string;
  entityParentId?: number | string;
  entityVersion?: string;
  canNavigateBack?: boolean;
  onNavigateBack?: () => void;
  fileIsEmpty?: boolean;
  applyChanges?: ApplyChangesMode;
  value?: MetadataEntityData;
  onChange?: (metadata: MetadataEntityData) => void | Promise<void>;
  downloadable?: boolean;
  showContent?: boolean;
  title?: string;
  titleStyle?: CSSProperties;
  removeAllAvailable?: boolean;
  restrictedKeys?: string[];
  extraKeys?: string[];
  extraInfo?: ReactNode[];
  specialTagsProperties?: Record<string, unknown>;
  pending?: boolean;
  metadataRenderFn?: () => ReactNode;
  showMetadata?: boolean;
  jobList?: ReactNode;
  openEditFileForm?: () => void;
};

export type MetadataContext = {
  entityId: number | string;
  entityClass: string;
  entityParentId?: number | string;
  entityVersion?: string;
  isDataStorageTags: boolean;
  metadataEntity?: MetadataEntityRef;
};

export type MetadataChangeItem = Pick<MetadataItem, 'key' | 'value' | 'type'>;
