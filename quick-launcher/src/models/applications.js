import apiGet from './base/api-get';
import configurations from './cloud-pipeline-api/configurations';
import metadata from './cloud-pipeline-api/metadata';
import metadataSearch from './cloud-pipeline-api/metadata-search';
import whoAmI from './cloud-pipeline-api/who-am-i';
import getTools from './cloud-pipeline-api/tools';
import getToolsImages from './cloud-pipeline-api/tools-images';
import fetchSettings from './base/settings';

function fetchTools(silent = false) {
  return new Promise((resolve, reject) => {
    const mapApplicationToolImage = images => app => {
      const {id} = app;
      if (images[id]) {
        return {
          ...app,
          iconData: images[id]
        };
      }
      return app;
    };
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
    fetchSettings()
      .then(settings => {
        whoAmI()
          .then(authInfoPayload => {
            const {payload: userInfo} = authInfoPayload;
            fetchAllUserStorages(
              settings,
              userInfo
            )
              .then(storages => {
                if (userInfo) {
                  userInfo.storages = storages.slice();
                }
                metadataSearch('TOOL', settings.tag, settings.tagValue)
                  .then((metadataSearchPayload) => {
                    const {payload: metadataPayload = []} = metadataSearchPayload;
                    const applicationIds = new Set(
                      metadataPayload
                        .map(m => Number(m.entityId))
                    );
                    if (applicationIds.size > 0) {
                      getTools()
                        .then((allTools) => {
                          const apps = allTools
                            .filter(tool => !tool.link)
                            .filter(tool => applicationIds.has(Number(tool.id)));
                          apps.sort((a, b) => {
                            if (a.name > b.name) {
                              return 1;
                            }
                            if (a.name < b.name) {
                              return -1;
                            }
                            return 0;
                          });
                          !silent && console.log('apps', apps);
                          const uniqueTools = new Set(
                            apps
                              .filter(tool => tool && tool.hasIcon)
                              .map(tool => Number(tool.id))
                          );
                          getToolsImages([...uniqueTools])
                            .then(images => {
                              resolve({
                                applications: apps.map(mapApplicationToolImage(images)),
                                userInfo
                              });
                            });
                        })
                        .catch(reject);
                    } else {
                      resolve({applications: []});
                    }
                  })
                  .catch(reject)
              });
          })
          .catch(reject);
      });
  });
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

export default function getApplications(silent = false) {
  if (!CPAPI) {
    return apiGet('applications');
  } else if (TOOLS) {
    // Cloud Pipeline API (tools):
    return fetchTools(silent);
  } else {
    // Cloud Pipeline API (configurations):
    return fetchConfigurations();
  }
}
