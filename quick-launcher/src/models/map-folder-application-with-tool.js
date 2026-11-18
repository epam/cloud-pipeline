import { processGatewaySpecParameters } from './parse-gateway-spec';

export function findToolForFolderApplication (folderApplication, tools) {
  const {
    appType,
    info: folderAppInfo = {}
  } = folderApplication;
  let tool;
  if (folderAppInfo && folderAppInfo.toolId) {
    tool = {id: folderAppInfo.toolId, version: folderAppInfo.toolVersion || 'latest'};
    console.log(`Application "${folderApplication.name}" has docker image specified in gateway.spec json: #${tool.id} (version ${tool.version})`);
  } else if (appType) {
    tool = tools.find(aTool => aTool._folder_ && aTool._folderAppType_ === appType);
    if (!tool) {
      console.warn(`Tool not found for application with "${appType || 'default'}" app type (${folderApplication.name}). Default tool will be used`);
    }
  }
  if (!tool) {
    tool = tools.find(aTool => aTool._folder_ && !aTool._folderAppType_);
  }
  if (!tool) {
    console.warn(`Tool not found for application with "${appType || 'default'}" app type (${folderApplication.name})`);
    return undefined;
  }
  return tool;
}

export default function mapFolderApplication (folderApplication, tools) {
  const tool = findToolForFolderApplication(folderApplication, tools);
  if (!tool) {
    return undefined;
  }
  return {
    ...folderApplication,
    toolId: tool.id,
    toolVersion: tool.version || 'latest',
    image: tool.image,
    hasIcon: !!folderApplication.icon,
    __launch_parameters__: {
      FOLDER_APPLICATION_STORAGE: {value: folderApplication.storage, type: 'number'},
      FOLDER_APPLICATION_PATH: {value: folderApplication.path, type: 'string'},
      ...processGatewaySpecParameters(folderApplication?.info?.parameters || {})
    }
  };
}
