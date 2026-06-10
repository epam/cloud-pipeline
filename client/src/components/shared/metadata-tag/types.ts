import {MetadataAttribute} from '../../../@types/metadata.ts';

export type NormalizedMetadataTag = {
  tag: string;
  type: string;
  value: MetadataAttribute['value'];
  secret: boolean;
};
