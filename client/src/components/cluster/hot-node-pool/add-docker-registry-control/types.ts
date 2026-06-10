import type {CSSProperties} from 'react';
import type {DockerRegistry, Tool, ToolGroup} from '../../../../@types/tools.ts';

export interface ToolVersionWithId {
  id?: number;
  version: string;
}

export interface EnrichedTool extends Omit<Tool, 'registry'> {
  dockerImage: string;
  registry: DockerRegistry;
  group: ToolGroup;
  name: string;
}

export interface ToolGroupOption {
  key: string;
  registryLabel: string;
  groupName: string;
  tools: EnrichedTool[];
}

export type AddDockerRegistryControlOnChangeSingle = (imageWithVersion: string) => void;

export type AddDockerRegistryControlOnChangeMultiple = (
  docker: string,
  versions: ToolVersionWithId[],
  toolId?: number,
) => void;

export interface AddDockerRegistryControlProps {
  disabled?: boolean;
  duplicate?: boolean;
  className?: string;
  docker?: string;
  showError?: boolean;
  showDelete?: boolean;
  style?: CSSProperties;
  onChange?: AddDockerRegistryControlOnChangeSingle | AddDockerRegistryControlOnChangeMultiple;
  onRemove?: () => void;
  multipleMode?: boolean;
  versionsSelected?: ToolVersionWithId[];
  containerStyle?: CSSProperties;
  imagesToExclude?: string[];
}

export interface AddDockerRegistryControlController {
  errorMessage?: string;
  showError: boolean;
  disabled?: boolean;
  duplicate?: boolean;
  className?: string;
  style?: CSSProperties;
  containerStyle?: CSSProperties;
  showDelete: boolean;
  multipleMode?: boolean;
  pending: boolean;
  docker?: string;
  dockerImageField?: string;
  filteredToolGroups: ToolGroupOption[];
  imagesToExclude?: string[];
  version?: string;
  versions: string[];
  versionsPending: boolean;
  versionsSelected: ToolVersionWithId[];
  dockerImageVersionField?: string;
  onChangeDockerImage: (image: string) => void;
  onChangeDockerVersion: (version: string) => void;
  onChangeMultipleVersions: (versions: string[]) => void;
  setDockerImageField: (value?: string) => void;
  setDockerImageVersionField: (value?: string) => void;
  onRemove?: () => void;
}
