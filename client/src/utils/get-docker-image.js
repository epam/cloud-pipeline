/**
 * Splits a `registry/group/tool[:version]` docker image, lower-cased.
 * @param {string} dockerImage
 * @returns {{registry: string, group: string, tool: string, version: string}|undefined} -
 * undefined for an image in any other format
 */
export function parseDockerImage (dockerImage) {
  const parts = (dockerImage || '').trim().toLowerCase().split('/');
  if (parts.length !== 3) {
    return undefined;
  }
  const [registry, group, toolAndVersion] = parts;
  const [tool, version = 'latest'] = toolAndVersion.split(':');
  return {registry, group, tool, version};
}

export function getDockerImage (dockerImage, dockerRegistries) {
  if (!dockerImage || !dockerRegistries) {
    return undefined;
  }
  if (!dockerRegistries.loaded) {
    (dockerRegistries.fetchIfNeededOrWait)();
    return undefined;
  }
  const {registries = []} = dockerRegistries.value;
  const parsed = parseDockerImage(dockerImage);
  if (!parsed) {
    return undefined;
  }
  const {
    registry: diRegistry,
    group: diGroup,
    tool: diTool,
    version: diVersion
  } = parsed;
  const registry = registries.find((r) => r.path.toLowerCase() === diRegistry);
  if (!registry) {
    return undefined;
  }
  const {groups = []} = registry;
  const group = groups.find((g) => g.name.toLowerCase() === diGroup);
  if (!group) {
    return undefined;
  }
  const {tools = []} = group;
  const tool = tools.find((t) => t.image.toLowerCase() === `${diGroup}/${diTool}`);
  if (!tool) {
    return undefined;
  }
  return {
    dockerImage,
    registry,
    group,
    tool,
    version: diVersion
  };
}
