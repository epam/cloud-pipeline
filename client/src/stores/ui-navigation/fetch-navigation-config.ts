import {getJsonPreferenceValue, getStringPreferenceValue} from '../../queries/preferences/hooks.ts';
import {preferenceNames} from '../preferences/names.ts';
import {getAuthenticatedUser} from '../users/hooks.ts';
import {loadMetadata} from '../../api/metadata/load-metadata.ts';
import {ROLE_CLASS, USER_CLASS} from './constants.ts';
import {mergeNavigationAttributes} from './merge-attributes.ts';
import {parseNavigationAttributes} from './parse-attributes.ts';
import {UiNavigationAttributes} from './types.ts';

async function fetchUserNavigationAttributes(userId: number): Promise<UiNavigationAttributes> {
  try {
    const [response] = await loadMetadata([{entityId: userId, entityClass: USER_CLASS}]);
    return parseNavigationAttributes(response?.data ?? {});
  } catch {
    return {};
  }
}

async function fetchRoleNavigationAttributes(
  roleIds: number[],
  initial: UiNavigationAttributes,
): Promise<UiNavigationAttributes> {
  if (!roleIds.length) {
    return initial;
  }
  try {
    const responses = await loadMetadata(
      roleIds.map((entityId) => ({entityId, entityClass: ROLE_CLASS})),
    );
    return mergeNavigationAttributes(
      responses.map((response) => response?.data ?? {}).filter(Boolean),
      initial,
    );
  } catch {
    return initial;
  }
}

async function fetchGroupNavigationAttributes(
  groups: string[],
  initial: UiNavigationAttributes,
): Promise<UiNavigationAttributes> {
  if (!groups.length) {
    return initial;
  }
  try {
    const groupsPreferences = await getJsonPreferenceValue<Record<string, Record<string, unknown>>>(
      preferenceNames.miscGroupsUiPreferences,
    );
    const groupSources = Object.entries(groupsPreferences ?? {})
      .filter(([group]) => groups.includes(group))
      .map(([, groupPreferences]) =>
        Object.fromEntries(
          Object.entries(groupPreferences ?? {}).map(([key, value]) => [key, {value}]),
        ),
      );
    return mergeNavigationAttributes(groupSources, initial);
  } catch {
    return initial;
  }
}

export async function fetchNavigationAttributes(): Promise<UiNavigationAttributes> {
  const user = getAuthenticatedUser();
  const roleIds = (user.roles ?? []).map((role) => role.id);
  const groupsAndRoles = [
    ...new Set([...(user.groups ?? []), ...(user.roles ?? []).map((role) => role.name)]),
  ];

  let attributes = await fetchUserNavigationAttributes(user.id);
  attributes = await fetchRoleNavigationAttributes(roleIds, attributes);
  attributes = await fetchGroupNavigationAttributes(groupsAndRoles, attributes);
  return attributes;
}

export async function fetchSupportTemplate(): Promise<string | undefined> {
  const user = getAuthenticatedUser();
  const templateRaw = await getStringPreferenceValue(preferenceNames.uiSupportTemplate);
  if (!templateRaw) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(templateRaw) as unknown;
    if (typeof parsed === 'string') {
      return parsed;
    }
    if (parsed && typeof parsed === 'object') {
      const groups = [...(user.roles ?? []).map((role) => role.name), ...(user.groups ?? [])];
      const record = parsed as Record<string, string>;
      const groupKey = Object.keys(record).find((key) => groups.includes(key));
      return groupKey ? record[groupKey] : record._default;
    }
  } catch {
    return templateRaw;
  }
  return undefined;
}

export async function isAiChatBotAvailable(): Promise<boolean> {
  const miscAi = await getJsonPreferenceValue<{api?: string}>(preferenceNames.miscAiPreferences);
  return Boolean(miscAi?.api?.length);
}
