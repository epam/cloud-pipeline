export default function parseUserDefinedLaunchOptions (appSettings, options) {
  const {
    nodeSize,
    ...result
  } = options || {};
  if (
    nodeSize &&
    appSettings &&
    appSettings.appConfigNodeSizes.hasOwnProperty(nodeSize)
  ) {
    result.instance_size = appSettings.appConfigNodeSizes[options.nodeSize];
  }
  return result;
}
