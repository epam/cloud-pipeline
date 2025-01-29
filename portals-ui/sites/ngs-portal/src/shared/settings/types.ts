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
  ngsProjectsRoot?: number | number[] | string | string [];
  /**
   * NGS pipelines root folder(s).
   */
  ngsPipelinesRoot?: number | number[] | string | string [];
};
