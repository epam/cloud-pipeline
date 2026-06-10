import type {DockerRegistry, Tool} from '../../../../@types/tools.ts';
import type {EnrichedTool, ToolGroupOption, ToolVersionWithId} from './types.ts';

export function buildToolGroups(registries: DockerRegistry[]): ToolGroupOption[] {
  const result: ToolGroupOption[] = [];

  for (const registry of registries) {
    for (const group of registry.groups ?? []) {
      const groupTools = group.tools ?? [];
      if (groupTools.length === 0) {
        continue;
      }

      const toolsGroup: ToolGroupOption = {
        key: `${registry.path}/${group.name}`,
        registryLabel: registry.description || registry.path,
        groupName: group.name,
        tools: [],
      };

      for (const tool of groupTools) {
        if (!/^windows$/i.test((tool as Tool & {platform?: string}).platform ?? '')) {
          toolsGroup.tools.push({
            ...tool,
            dockerImage: `${registry.path}/${tool.image}`,
            registry,
            group,
            name: tool.image.split('/').pop() ?? '',
          });
        }
      }

      result.push(toolsGroup);
    }
  }

  return result;
}

export function findToolByDockerImage(
  docker: string | undefined,
  registries: DockerRegistry[],
): Tool | null {
  if (!docker) {
    return null;
  }

  const normalizedDocker = docker.toLowerCase();

  for (const registry of registries) {
    for (const group of registry.groups ?? []) {
      for (const tool of group.tools ?? []) {
        const image = `${registry.path}/${tool.image}`.toLowerCase();
        if (normalizedDocker === image) {
          return tool;
        }
      }
    }
  }

  return null;
}

export function getToolId(
  docker: string | undefined,
  toolGroups: ToolGroupOption[],
): number | undefined {
  if (!docker) {
    return undefined;
  }

  const [registryPath, groupName] = docker.split('/');
  const currentGroup = toolGroups.find((group) => group.key === `${registryPath}/${groupName}`);
  if (!currentGroup) {
    return undefined;
  }

  const currentImage = currentGroup.tools.find((image) => image.dockerImage === docker);
  return currentImage?.id;
}

export function filterToolGroups(
  toolGroups: ToolGroupOption[],
  docker: string | undefined,
  dockerImageField: string | undefined,
): ToolGroupOption[] {
  return toolGroups
    .map((group) => ({
      ...group,
      tools: group.tools.filter(
        (tool) =>
          tool.dockerImage === docker ||
          (dockerImageField &&
            dockerImageField.length >= 3 &&
            tool.dockerImage.toLowerCase().includes(dockerImageField.toLowerCase())),
      ),
    }))
    .filter((group) => group.tools.length > 0);
}

export function mapToolTagsToVersions(
  tags: Array<{id?: number; version?: string; platform?: string}>,
): ToolVersionWithId[] {
  return tags
    .filter((tag) => !tag.platform || !/^windows$/i.test(tag.platform))
    .map((tag) => ({id: tag.id, version: tag.version ?? ''}))
    .filter((tag) => tag.version);
}

export function pickVersion(
  currentVersion: string | undefined,
  versions: string[],
): string | undefined {
  if (versions.length === 0) {
    return currentVersion;
  }

  if (versions.some((version) => version.toLowerCase() === (currentVersion ?? '').toLowerCase())) {
    return currentVersion;
  }

  if (versions.includes('latest')) {
    return 'latest';
  }

  return versions[0];
}
