export function getDockerImage(dockerImage, dockerRegistries) {
  if (!dockerImage || !dockerRegistries) {
    return undefined;
  }
  if (!dockerRegistries.loaded) {
    dockerRegistries.fetchIfNeededOrWait();
    return undefined;
  }
  const {registries = []} = dockerRegistries.value;
  const parts = dockerImage.trim().toLowerCase().split('/');
  if (parts.length !== 3) {
    return undefined;
  }
  const [diRegistry, diGroup, diToolAndVersion] = parts;
  const [diTool, diVersion = 'latest'] = diToolAndVersion.split(':');
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
    version: diVersion,
  };
}
