import {parseNavigationAttributes} from './parse-attributes.ts';
import {UiNavigationAttributes} from './types.ts';

type MetadataAttributes = Record<string, {value?: unknown}>;

export function mergeNavigationAttributes(
  sources: MetadataAttributes[],
  initial: UiNavigationAttributes = {},
): UiNavigationAttributes {
  let pages = initial.pages ? [...initial.pages] : undefined;
  let dashboard = initial.dashboard;
  let homePage = initial.homePage;
  let searchDocumentTypes = initial.searchDocumentTypes;
  let libraryExpanded = initial.libraryExpanded;
  let launchForm = initial.launchForm;
  let runLogsMainTask = initial.runLogsMainTask;

  for (const source of sources) {
    const parsed = parseNavigationAttributes(source, {
      libraryExpanded: libraryExpanded !== undefined,
    });

    if (parsed.pages?.length) {
      const allowed = new Set(pages ?? []);
      pages = [];
      for (const page of parsed.pages) {
        if (allowed.size === 0 || allowed.has(page)) {
          pages.push(page);
        }
      }
    }

    if (parsed.dashboard !== undefined && dashboard === undefined) {
      dashboard = parsed.dashboard;
    }
    if (parsed.homePage && !homePage) {
      homePage = parsed.homePage;
    }
    if (parsed.searchDocumentTypes && !searchDocumentTypes) {
      searchDocumentTypes = parsed.searchDocumentTypes;
    }
    if (parsed.libraryExpanded !== undefined) {
      libraryExpanded = parsed.libraryExpanded;
    }
    if (parsed.launchForm && !launchForm) {
      launchForm = parsed.launchForm;
    }
    if (parsed.runLogsMainTask !== undefined && runLogsMainTask === undefined) {
      runLogsMainTask = parsed.runLogsMainTask;
    }
  }

  return {
    pages: pages?.length ? pages : undefined,
    dashboard,
    homePage,
    searchDocumentTypes,
    libraryExpanded,
    launchForm,
    runLogsMainTask,
  };
}
