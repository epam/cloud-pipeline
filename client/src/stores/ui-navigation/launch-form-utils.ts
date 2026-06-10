import type {LaunchFormSettings} from './types.ts';

const SECTIONS = {
  logs: 'logs',
  tools: 'tools',
  pipelines: 'pipelines',
} as const;

function getSectionBoolean(
  launchFormSettings: LaunchFormSettings | undefined,
  section: string,
  keys: string[],
  defaultValue = true,
): boolean {
  const config = launchFormSettings?.[section] ?? {};
  for (const key of keys) {
    if (Object.hasOwn(config, key)) {
      return `${config[key]}`.toLowerCase() === 'true';
    }
  }
  return defaultValue;
}

export function estimatedPriceVisible(launchFormSettings?: LaunchFormSettings) {
  return {
    logs: getSectionBoolean(launchFormSettings, SECTIONS.logs, [
      'estimates-visible',
      'estimated-price-visible',
    ]),
    pipelines: getSectionBoolean(launchFormSettings, SECTIONS.pipelines, [
      'estimates-visible',
      'estimated-price-visible',
    ]),
    tools: getSectionBoolean(launchFormSettings, SECTIONS.tools, [
      'estimates-visible',
      'estimated-price-visible',
    ]),
  };
}

export function showOptionalParametersFilter(launchFormSettings?: LaunchFormSettings) {
  return {
    pipelines: getSectionBoolean(
      launchFormSettings,
      SECTIONS.pipelines,
      ['optional-parameters-filter'],
      false,
    ),
    tools: getSectionBoolean(
      launchFormSettings,
      SECTIONS.tools,
      ['optional-parameters-filter'],
      false,
    ),
  };
}
