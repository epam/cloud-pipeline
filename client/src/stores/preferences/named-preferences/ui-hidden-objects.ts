import {
  useJsonPreferenceValue,
  usePreferenceInitialized,
} from '../../../queries/preferences/hooks.ts';
import {preferenceNames} from '../names.ts';
import {useMemo} from 'react';
import {asNumberArray, asStringArray} from '../../../utilities/arrays.ts';

export type HiddenObjectsData = {
  dataStorages: number[];
  folders: number[];
  pipelines: number[];
  configurations: number[];
  pipelineVersions: Array<{pipelineId: number; version: string}>;
  tools: number[];
  toolGroups: number[];
  toolRegistries: number[];
  metadata: number[];
  metadataClasses: Array<{metadataId: number; className: string}>;
};

export interface HiddenObjectsInterface {
  folderIsHidden(folderId: number): boolean;
  pipelineIsHidden(pipelineId: number): boolean;
  pipelineVersionIsHidden(pipelineId: number, version: string): boolean;
  configurationIsHidden(configurationId: number): boolean;
  dataStorageIsHidden(dataStorageId: number): boolean;
  toolIsHidden(toolId: number): boolean;
  toolGroupIsHidden(toolGroupId: number): boolean;
  toolRegistryIsHidden(toolRegistryId: number): boolean;
  metadataFolderIsHidden(metadataId: number): boolean;
  metadataClassIsHidden(metadataId: number, className: string): boolean;
}

export type HiddenObjectsConfig = HiddenObjectsData & HiddenObjectsInterface;

function buildHiddenObjectsConfig(data: HiddenObjectsData): HiddenObjectsConfig {
  return {
    ...data,
    folderIsHidden(folderId: number): boolean {
      return data.folders.includes(folderId);
    },
    pipelineIsHidden(pipelineId: number): boolean {
      return data.pipelines.includes(pipelineId);
    },
    pipelineVersionIsHidden(pipelineId: number, version: string): boolean {
      return data.pipelineVersions.some(
        (v) => v.pipelineId === pipelineId && v.version === version,
      );
    },
    configurationIsHidden(configurationId: number): boolean {
      return data.configurations.includes(configurationId);
    },
    dataStorageIsHidden(dataStorageId: number): boolean {
      return data.dataStorages.includes(dataStorageId);
    },
    toolIsHidden(toolId: number): boolean {
      return data.tools.includes(toolId);
    },
    toolGroupIsHidden(toolGroupId: number): boolean {
      return data.toolGroups.includes(toolGroupId);
    },
    toolRegistryIsHidden(toolRegistryId: number): boolean {
      return data.toolRegistries.includes(toolRegistryId);
    },
    metadataFolderIsHidden(metadataId: number): boolean {
      return data.metadata.includes(metadataId);
    },
    metadataClassIsHidden(metadataId: number, className: string): boolean {
      return data.metadataClasses.some(
        (m) => m.metadataId === metadataId && m.className === className,
      );
    },
  };
}

export function useUiHiddenObjects(): {config: HiddenObjectsConfig; initialized: boolean} {
  const pr = useJsonPreferenceValue(preferenceNames.uiHiddenObjects);
  const initialized = usePreferenceInitialized(preferenceNames.uiHiddenObjects);
  const config = useMemo<HiddenObjectsData>(() => {
    if (!pr) {
      return {
        dataStorages: [],
        folders: [],
        pipelines: [],
        pipelineVersions: [],
        configurations: [],
        tools: [],
        toolGroups: [],
        toolRegistries: [],
        metadata: [],
        metadataClasses: [],
      };
    }
    const {
      // eslint-disable-next-line camelcase
      data_storage = [],
      // eslint-disable-next-line camelcase
      data_storages = data_storage,
      dataStorage = data_storages,
      dataStorages: _dataStorages = dataStorage,
      folder = [],
      folders: _folders = folder,
      pipeline = [],
      pipelines: _pipelines = pipeline,
      // eslint-disable-next-line camelcase
      pipeline_version = [],
      // eslint-disable-next-line camelcase
      pipeline_versions = pipeline_version,
      pipelineVersion = pipeline_versions,
      pipelineVersions: _pipelineVersions = pipelineVersion,
      configuration = [],
      configurations: _configurations = configuration,
      tool = [],
      tools: _tools = tool,
      // eslint-disable-next-line camelcase
      tool_group = [],
      // eslint-disable-next-line camelcase
      tool_groups = tool_group,
      toolGroup = tool_groups,
      toolGroups: _toolGroups = toolGroup,
      // eslint-disable-next-line camelcase
      tool_registry = [],
      // eslint-disable-next-line camelcase
      tool_registries = tool_registry,
      toolRegistry = tool_registries,
      toolRegistries: _toolRegistry = toolRegistry,
      // eslint-disable-next-line camelcase
      metadata_folder = [],
      // eslint-disable-next-line camelcase
      metadata_folders = metadata_folder,
      metadataFolder = metadata_folders,
      metadataFolders: _metadataFolders = metadataFolder,
      // eslint-disable-next-line camelcase
      metadata_class = [],
      // eslint-disable-next-line camelcase
      metadata_classes = metadata_class,
      metadataClass = metadata_classes,
      metadataClasses: _metadataClasses = metadataClass,
    } = pr as Record<string, unknown>;
    const dataStorages = asNumberArray(_dataStorages);
    const folders = asNumberArray(_folders);
    const pipelines = asNumberArray(_pipelines);
    const configurations = asNumberArray(_configurations);
    const tools = asNumberArray(_tools);
    const toolGroups = asNumberArray(_toolGroups);
    const pipelineVersionsRaw = asStringArray(_pipelineVersions);
    const pipelineVersions = pipelineVersionsRaw
      .map((v) => v.split('/'))
      .filter((v) => v.length === 2)
      .filter((v) => v[0].trim() !== '' && v[1] !== '')
      .map((v) => ({
        pipelineId: Number(v[0]),
        version: v[1],
      }))
      .filter((v) => !Number.isNaN(v.pipelineId) && Number.isFinite(v.pipelineId));
    const toolRegistries = asNumberArray(_toolRegistry);
    const metadata = asNumberArray(_metadataFolders);
    const metadataClassesRaw = asStringArray(_metadataClasses);
    const metadataClasses = metadataClassesRaw
      .map((v) => v.split('/'))
      .filter((v) => v.length === 2)
      .filter((v) => v[0].trim() !== '' && v[1] !== '')
      .map((v) => ({
        metadataId: Number(v[0]),
        className: v[1],
      }))
      .filter((v) => !Number.isNaN(v.metadataId) && Number.isFinite(v.metadataId));
    return {
      dataStorages,
      pipelines,
      pipelineVersions,
      configurations,
      folders,
      tools,
      toolGroups,
      toolRegistries,
      metadata,
      metadataClasses,
    };
  }, [pr]);
  return useMemo(
    () => ({
      config: buildHiddenObjectsConfig(config),
      initialized,
    }),
    [config, initialized],
  );
}
