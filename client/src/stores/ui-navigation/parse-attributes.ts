import {
  DASHBOARD_CONFIGURATION_ATTRIBUTE,
  HOME_PAGE_ATTRIBUTE,
  LAUNCH_FORM_ATTRIBUTE,
  LIBRARY_EXPANDED_ATTRIBUTE,
  RUN_LOGS_MAIN_TASK_ATTRIBUTE,
  SEARCH_PAGE_SECTIONS_ATTRIBUTE,
  UI_PAGES_ATTRIBUTE,
} from './constants.ts';
import {LaunchFormSettings, UiNavigationAttributes} from './types.ts';

type MetadataAttributes = Record<string, {value?: unknown}>;

type ParseAttributesIgnore = {
  dashboard?: boolean;
  pages?: boolean;
  homePage?: boolean;
  searchDocumentTypes?: boolean;
  libraryExpanded?: boolean;
  launchForm?: boolean;
  runLogs?: boolean;
};

function parseCommaSeparatedList(value: unknown): string[] | undefined {
  if (typeof value !== 'string') {
    return undefined;
  }
  const items = value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  return items.length ? items : undefined;
}

function parseDashboard(value: unknown): unknown {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value);
    } catch (error) {
      console.warn(
        'Error parsing dashboard settings:',
        error instanceof Error ? error.message : error,
      );
      return undefined;
    }
  }
  return value;
}

export function parseNavigationAttributes(
  data: MetadataAttributes,
  ignore: ParseAttributesIgnore = {},
): UiNavigationAttributes {
  const result: UiNavigationAttributes = {};

  if (!ignore.pages && Object.hasOwn(data, UI_PAGES_ATTRIBUTE)) {
    result.pages = parseCommaSeparatedList(data[UI_PAGES_ATTRIBUTE]?.value);
  }

  if (!ignore.dashboard && Object.hasOwn(data, DASHBOARD_CONFIGURATION_ATTRIBUTE)) {
    result.dashboard = parseDashboard(data[DASHBOARD_CONFIGURATION_ATTRIBUTE]?.value);
  }

  if (!ignore.homePage && Object.hasOwn(data, HOME_PAGE_ATTRIBUTE)) {
    const value = data[HOME_PAGE_ATTRIBUTE]?.value;
    result.homePage = typeof value === 'string' ? value : undefined;
  }

  if (!ignore.libraryExpanded && Object.hasOwn(data, LIBRARY_EXPANDED_ATTRIBUTE)) {
    result.libraryExpanded =
      `${data[LIBRARY_EXPANDED_ATTRIBUTE]?.value ?? ''}`.toLowerCase() === 'true';
  }

  if (!ignore.searchDocumentTypes && Object.hasOwn(data, SEARCH_PAGE_SECTIONS_ATTRIBUTE)) {
    result.searchDocumentTypes = parseCommaSeparatedList(
      data[SEARCH_PAGE_SECTIONS_ATTRIBUTE]?.value,
    );
  }

  if (!ignore.launchForm && Object.hasOwn(data, LAUNCH_FORM_ATTRIBUTE)) {
    const value = data[LAUNCH_FORM_ATTRIBUTE]?.value;
    if (value && typeof value === 'object') {
      result.launchForm = value as LaunchFormSettings;
    } else if (typeof value === 'string') {
      try {
        result.launchForm = JSON.parse(value) as LaunchFormSettings;
      } catch {
        result.launchForm = undefined;
      }
    }
  }

  if (!ignore.runLogs && Object.hasOwn(data, RUN_LOGS_MAIN_TASK_ATTRIBUTE)) {
    result.runLogsMainTask =
      `${data[RUN_LOGS_MAIN_TASK_ATTRIBUTE]?.value ?? 'false'}`.toLowerCase() === 'true';
  }

  return result;
}
