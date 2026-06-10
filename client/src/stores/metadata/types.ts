import {LoadableObject} from '../../@types/common.ts';
import {MetadataLoadResponseItem} from '../../@types/metadata.ts';
import {TimestampedObject} from '../../utilities/caching.ts';

export type MetadataEntityInfo = LoadableObject & TimestampedObject & MetadataLoadResponseItem;
