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
};
