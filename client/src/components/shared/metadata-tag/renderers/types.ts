import type {ComponentType} from 'react';
import type {CommonProps} from '../../../../@types/common.ts';
import type {MetadataAttribute} from '../../../../@types/metadata.ts';

export type MetadataValueContext = {
  tag: string;
  value: string | number | boolean | null | undefined;
  secret: boolean;
  type: string;
  raw: MetadataAttribute | MetadataAttribute['value'];
};

export type MetadataValueRendererProps = CommonProps & MetadataValueContext;

export type MetadataValueRenderer = ComponentType<MetadataValueRendererProps>;

export type ParsedJsonItems = {
  keys: string[];
  items: Record<string, unknown>[];
  length: number;
};
