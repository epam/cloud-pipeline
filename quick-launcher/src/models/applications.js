import apiGet from './base/api-get';
import configurations from './cloud-pipeline-api/configurations';
import metadata from './cloud-pipeline-api/metadata';
import metadataSearch from './cloud-pipeline-api/metadata-search';
import whoAmI from './cloud-pipeline-api/who-am-i';
import getTools from './cloud-pipeline-api/tools';
import getToolsImages from './cloud-pipeline-api/tools-images';
import fetchSettings from './base/settings';
import { getApplicationTypeSettings, getFolderApplicationTypes } from "./folder-application-types";

export function nameSorter(a, b) {
  if (a.name > b.name) {
    return 1;
  }
  if (a.name < b.name) {
    return -1;
  }
  return 0;
}

async function fetchToolsByTags(keyValuePairs = []) {
  const searchToolsByKeyValue = async (opts) => {
    const {
      key,
      value,
      ...rest
    } = opts;
    const {payload: metadata = []} = await metadataSearch('TOOL', key, value);
    return metadata.map(metadataItem => ({id: Number(metadataItem.entityId), ...rest}));
  };
  const results = await Promise.all(
    keyValuePairs.map(searchToolsByKeyValue)
  );
  return results.reduce((r, c) => ([...r, ...c]), []);
}

function mapApplicationToolImage (images) {
  return function map (app) {
    const {id} = app;
    if (images[id]) {
      return {
        ...app,
        iconData: images[id]
      };
    }
    return app;
  }
}

async function fetchTools(opts = {}) {
  const {
    silent = false,
    folder = false,
  } = opts;
  console.log('fetch tools', opts);
  const fetchAllUserStorages = (settings, userInfo) => {
    if (!settings || !settings.userStoragesAttribute || !userInfo) {
      return Promise.resolve([]);
    }
    // const {roles = []} = userInfo;
    const entities = [
      {entityId: userInfo.id, entityClass: 'PIPELINE_USER'},
      // ...roles.map(role => ({entityId: role.id, entityClass: 'ROLE'}))
    ]
    return new Promise((resolve) => {
      metadata(entities)
        .then(result => {
          const {payload = []} = result;
          const storagesArray = payload
            .map(entry => (entry.data || {})[settings.userStoragesAttribute])
            .filter(Boolean)
            .map(attr => (attr.value || '').split(',').map(s => s.trim()))
            .reduce((acc, r) => ([...acc, ...r]))
            .map(s => +s)
            .filter(i => !Number.isNaN(i));
          resolve(Array.from(new Set(storagesArray)));
        })
        .catch(() => resolve([]));
    });
  };
  const settings = await fetchSettings();
  const authInfoPayload = await whoAmI();
  const {payload: userInfo} = authInfoPayload;
  const storages = await fetchAllUserStorages(
    settings,
    userInfo
  );
  if (userInfo) {
    userInfo.storages = storages.slice();
  }
  const tagsRequest = [{key: settings.tag, value: settings.tagValue, _folder_: false}];
  if (folder) {
    const applicationTypes = getFolderApplicationTypes(settings);
    const folderTags = applicationTypes.map(appType => {
      const appTypeSettings = getApplicationTypeSettings(settings, appType);
      const hasFolderAppsTagSettings = appTypeSettings.folderAppTag && appTypeSettings.folderAppTagValue;
      if (hasFolderAppsTagSettings) {
        return {
          key: appTypeSettings.folderAppTag,
          value: appTypeSettings.folderAppTagValue,
          _folder_: true,
          _folderAppType_: appType
        };
      }
      return {
        key: appTypeSettings.tag,
        value: appTypeSettings.tagValue,
        _folder_: true,
        _folderAppType_: appType
      };
    }).filter(Boolean);
    tagsRequest.push(...folderTags);
  }
  const ids = await fetchToolsByTags(tagsRequest);
  const versionsInfoTags = [];
  if (settings.latestTag) {
    versionsInfoTags.push({
      key: settings.latestTag,
      value: 'true',
      latest: true
    });
  }
  if (settings.deprecatedTag) {
    versionsInfoTags.push({
      key: settings.deprecatedTag,
      value: 'true',
      deprecated: true
    });
  }
  const toolVersionsInfo = await fetchToolsByTags(versionsInfoTags);
  const allTools = await getTools();

  function matchTool (toolIdConfig, allTools = []) {
    const {
      id,
      ...rest
    } = toolIdConfig || {};
    if (!id) {
      return [];
    }
    const versionsInfo = toolVersionsInfo
      .filter((o) => Number(o.id) === Number(id));
    const merged = versionsInfo.reduce((r, c) => ({...r, ...c}), {});
    const app = allTools
      .filter(tool => !tool.link)
      .find(tool => Number(tool.id) === Number(id));
    if (!app) {
      return undefined;
    }
    return {
      ...app,
      ...rest,
      ...merged
    };
  }
  const tools = ids.map(id => matchTool(id, allTools)).filter(Boolean);
  !silent && console.log('tools:', tools);
  const uniqueTools = new Set(
    tools
      .filter(tool => tool && tool.hasIcon)
      .map(tool => Number(tool.id))
  );
  const images = await getToolsImages([...uniqueTools]);
  console.log('images', images);
  return {
    applications: tools.map(mapApplicationToolImage(images)),
    userInfo
  };
}

function fetchConfigurations() {
  return new Promise((resolve, reject) => {
    const mapApplication = app => {
      const [config] = (app.entries || []).filter(e => e.default)
      return {
        id: app.id,
        name: app.name,
        description: app.description,
        configuration: config
      };
    };
    const mapApplicationTool = tools => app => {
      const {configuration} = app;
      if (configuration && configuration.configuration) {
        const {docker_image} = configuration.configuration;
        if (docker_image) {
          const [tool] = tools.filter(t => t.imageRegExp.test(docker_image));
          if (tool) {
            return {
              ...app,
              tool
            };
          }
        }
      }
      return app;
    };
    const mapApplicationToolImage = images => app => {
      const {tool} = app;
      if (tool && images[tool.id]) {
        return {
          ...app,
          iconData: images[tool.id]
        };
      }
      return app;
    };
    fetchSettings()
      .then(settings => {
        whoAmI()
          .then(authInfoPayload => {
            const {payload: userInfo} = authInfoPayload;
            configurations()
              .then((result) => {
                const {status, message, payload} = result;
                if (status === 'OK') {
                  const allConfigurations = payload || [];
                  const entities = allConfigurations.map(app => ({entityId: app.id, entityClass: 'CONFIGURATION'}));
                  metadata(entities)
                    .then((metadataResult) => {
                      const {payload: metadataPayload = []} = metadataResult;
                      if (metadataPayload) {
                        const applicationIds = new Set(
                          metadataPayload
                            .filter(m => m.data
                              && m.data[settings.tag]
                              && settings.tagValueRegExp.test(m.data[settings.tag].value)
                            )
                            .map(m => Number(m.entity.entityId))
                        );
                        const apps = allConfigurations
                          .filter(configuration => applicationIds.has(Number(configuration.id)))
                          .map(mapApplication);
                        getTools()
                          .then((tools) => {
                            const appsWithTools = apps.map(mapApplicationTool(tools));
                            const uniqueTools = new Set(
                              appsWithTools
                                .map(app => app.tool)
                                .filter(tool => tool && tool.hasIcon)
                                .map(tool => Number(tool.id))
                            );
                            getToolsImages([...uniqueTools])
                              .then(images => {
                                resolve({
                                  applications: appsWithTools.map(mapApplicationToolImage(images)),
                                  userInfo
                                });
                              });
                          })
                          .catch(reject);
                      } else {
                        resolve({applications: []});
                      }
                    })
                    .catch(reject);
                } else {
                  reject(new Error(message || `Error fetching configurations (status ${status})`));
                }
              })
              .catch(reject);
          })
          .catch(reject);
      });
  });
}

export default function getApplications(opts) {
  if (!CPAPI) {
    return apiGet('applications');
  } else if (TOOLS) {
    // Cloud Pipeline API (tools):
    return fetchTools(opts);
  } else {
    // Cloud Pipeline API (configurations):
    return fetchConfigurations();
  }
}
