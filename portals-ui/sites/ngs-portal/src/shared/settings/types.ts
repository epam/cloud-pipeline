export type LaunchSettings = {
  /**
   * Parameters to include when launching a pipeline
   */
  parameters?: Record<string, string | number | boolean>;
};

export type RunsFilterSettings = {
  /**
   * Parameters to include for filtering runs.
   * By default, settings.launchSettings.parameters will be used
   */
  parameters?: Record<string, string | number | boolean>;
};

export type NgsTaggedObjectSettings = {
  /**
   * If `tagsToDisplay` is set, only these tags will be displayed
   */
  tagsToDisplay?: string | string[];
  /**
   * If `tagsToHide` is set, these tags won't be displayed
   */
  tagsToHide?: string | string[];
  /**
   * Tags to filter projects by; if omitted, displayed tags will be used for filtering
   * (respecting `tagsToDisplay` and `tagsToHide` settings)
   */
  filterTags?: string | string[];
};

export type NgsProjectSettings = NgsTaggedObjectSettings & {
  /**
   * Tag for default project data storage id / name.
   * Default: `DataStorage`
   */
  dataStorageTag?: string;
  /**
   * Tag for project data storages (identifiers / names, comma-semicolon-space separated, JSON array)
   * Default: empty
   */
  dataStoragesTag?: string;
};

export type NgsPipelineSettings = NgsTaggedObjectSettings;

export type Settings = {
  /**
   * Cloud Pipeline API endpoint
   */
  api: string;
  /**
   * NGS projects root folder(s).
   * NGS projects will be read from these root folders (without sub-folders search);
   * new projects will be stored to the first root folder in the list.
   */
  ngsProjectsRoot?: number | number[] | string | string[];
  /**
   * NGS pipelines root folder(s).
   */
  ngsPipelinesRoot?: number | number[] | string | string[];
  /**
   * Launch settings
   */
  launchSettings?: LaunchSettings;
  /**
   * Runs lists predefined filters
   */
  runsFilter?: RunsFilterSettings;
  /**
   * NGS Project display configuration
   */
  ngsProject?: NgsProjectSettings;
  /**
   * NGS Pipeline display configuration
   */
  ngsPipeline?: NgsPipelineSettings;
  /**
   * NGS Run tasks dynamic attributes columns
   */
  runTasksAttributesColumns: string[];
};
