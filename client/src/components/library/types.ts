import {LibraryEntity, LibraryRootFolder} from '../../@types/library.ts';
import {ReactNode} from 'react';
import {MetadataEntityData} from '../../@types/metadata.ts';
import {Revision} from '../../@types/pipeline.ts';

export enum LibraryItemType {
  back = 'back',
  library = 'library',
  folder = 'folder',
  project = 'project',
  storage = 'storage',
  storages = 'storages',
  pipeline = 'pipeline',
  pipelines = 'pipelines',
  pipelineVersion = 'pipeline-version',
  configuration = 'configuration',
  projectHistory = 'project-history',
  metadata = 'metadata',
  metadataClass = 'metadata-class',
  loading = 'loading',
}

export type LibraryItem<Type extends LibraryItemType = LibraryItemType> = {
  id: string;
  type: Type;
  name: ReactNode;
  details?: ReactNode;
  level: number;
  parentIndex: number;
  expanded: boolean;
  expandable: boolean;
  visible: boolean;
  pending: boolean;
  object: LibraryEntity | LibraryRootFolder;
  revision?: Revision;
  metadata: MetadataEntityData;
  issuesCount: number;
  interactive: boolean;
  searchableParts: string[];
  searchHit: boolean;
  url: string | undefined;
};
