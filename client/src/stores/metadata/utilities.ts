import {MetadataEntityInfo} from './types.ts';

/**
 * Merges duplicate metadata entities (the one with the "fresh" timestamp wins)
 * @param infos
 */
export function mergeMetadataEntityInfos(infos: MetadataEntityInfo[]): MetadataEntityInfo[] {
  const result: MetadataEntityInfo[] = [];
  const sorted = infos.slice().sort((a, b) => b.timestamp - a.timestamp);
  const usedKeys = new Set<string>();
  for (const info of sorted) {
    if (!info.entity) {
      continue;
    }
    const key = `${info.entity.entityId}-${info.entity.entityClass}`;
    if (usedKeys.has(key)) {
      continue;
    }
    usedKeys.add(key);
    result.push(info);
  }
  return result;
}
