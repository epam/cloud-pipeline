import apiGet from './base/api-get';
import getRun from './cloud-pipeline-api/run';
import getRunTasks from './cloud-pipeline-api/run-tasks';
import getApplicationRun from './cloud-pipeline-api/get-application-run';
import getAvailableNode from './cloud-pipeline-api/get-available-node';
import launchConfigurationRequest from './cloud-pipeline-api/launch-configuration';
import launchToolRequest from './cloud-pipeline-api/launch-tool';
import resumeRun from './cloud-pipeline-api/resume-run';
import getToolSettings from './cloud-pipeline-api/get-tool-settings';
import getUser from './cloud-pipeline-api/get-user';
import parseRunServiceUrl from './cloud-pipeline-api/utilities/parse-run-service-url';
import launches from './cloud-pipeline-api/active-launches';
import fetchSettings from './base/settings';
import shareRun from './cloud-pipeline-api/share-run';
import generatePrettyUrl from './pretty-url-generator';
import parseLimitMounts from './limit-mounts-parser';
import {attachParameters, generateParameters} from './generate-parameters';
import joinLaunchOptions from './join-launch-options';
import parseGatewaySpec from './parse-gateway-spec';
import {findUserRun} from './find-user-run';
import applicationAvailable from "./folder-applications/application-available";

function pollRun(run, resolve, reject, initialPoll = false, appSettings) {
  const {id, status, initialized, serviceUrl} = run || {};
  if (initialPoll) {
    resolve({
      run,
      status: 'pending'
    });
    return;
  }
  switch (status) {
    case 'PAUSING':
    case 'RESUMING':
      console.log(`Run #${run.id} status: ${status}. Waiting...`);
      resolve({status: 'pending', run});
      break;
    case 'PAUSED':
      console.log(`Resuming run #${run.id}`);
      resumeRun(run.id)
        .then(() => {
          resolve({status: 'pending', run});
        })
        .catch(reject);
      break;
    case 'RUNNING':
      const urls = parseRunServiceUrl(serviceUrl);
      const infoParts = [
        status,
        initialized && 'INITIALIZED',
        urls.length && `SERVICES=${urls.map(u => u.name || u.url).join(', ')}`
      ]
        .filter(Boolean);
      if (initialized && urls.length > 0) {
        const {url} = urls[0];
        if (appSettings && appSettings.redirectAfterTaskFinished) {
          getRunTasks(run.id)
            .then(result => {
              const {status: requestStatus, message, payload: tasks} = result || {};
              if (requestStatus === 'OK') {
                const task = (tasks || []).find(t => t.name === appSettings.redirectAfterTaskFinished);
                if (task) {
                  infoParts.push(`[${appSettings.redirectAfterTaskFinished}]: ${task.status}`)
                } else {
                  infoParts.push(`[${appSettings.redirectAfterTaskFinished}]: missing`);
                }
                console.log(`Run #${id} info: ${infoParts.join('; ')}`);
                if (task && /^success$/i.test(task.status)) {
                  resolve({status: 'ready', initialized, url, run});
                } else if (task && /^(stopped|failure)$/i.test(task.status)) {
                  reject(new Error(`Error launching configuration: run task "${task.name}" status is ${task.status}`));
                } {
                  resolve({status: 'pending', initialized, url, run});
                }
              } else {
                console.log(`Run #${id} info: ${infoParts.join('; ')}`);
                reject(new Error(message || `Error fetching tasks statuses: request status ${requestStatus}`));
              }
            })
            .catch(reject);
        } else {
          console.log(`Run #${id} info: ${infoParts.join('; ')}`);
          resolve({status: 'ready', initialized, url, run});
        }
      } else {
        console.log(`Run #${id} info: ${infoParts.join('; ')}`);
        resolve({status: 'pending', initialized, run});
      }
      break;
    default:
      console.log(`Error launching configuration: run status is ${status}`);
      console.log('Run info:');
      console.log(JSON.stringify(run, undefined, ' '));
      reject(new Error(`Error launching configuration: run status is ${status}`))
      break;
  }
}

function launchConfiguration(application, user, options) {
  const runIdKey = options?.__run_id_key__ || `${application.id}-${user?.userName}`;
  return new Promise((resolve, reject) => {
    launchConfigurationRequest(
      application.id,
      application.configuration
    )
      .then(result => {
        const {payload = [], status, message} = result;
        if (status === 'OK') {
          if (payload.length > 0 && payload[0].id) {
            console.log(`Run #${payload[0].id} launched`);
            launches.set(runIdKey, payload[0].id);
            pollRun(payload[0], resolve, reject);
          } else {
            reject(new Error('Error launching configuration: unknown run id (empty response from restapi/runConfiguration)'));
          }
        } else {
          reject(new Error(message || `Error launching configuration: status ${status}`));
        }
      })
      .catch(reject);
  });
}

function launchTool(application, user, options) {
  const runIdKey = options?.__run_id_key__ || `${application.id}-${user?.userName}`;
  const {
    user: appOwner,
    version
  } = options || {};
  console.log('launchTool.options', options);
  const getAppOwnerInfo = () => {
    return new Promise((resolve, reject) => {
      if (!appOwner) {
        resolve();
      } else {
        getUser(appOwner)
          .then(resolve)
          .catch(e => {
            console.warn(`Error fetching application owner info: ${e.message}`);
            resolve({});
          });
      }
    });
  };
  return new Promise((resolve, reject) => {
    const handleReject = (e) => {
      console.log('HANDLE REJECT', e.message, runIdKey);
      launches.delete(runIdKey);
      reject(e);
    };
    const getAvailableNodeWrapper = (settings) => {
      if (settings && settings.useParentNodeId) {
        return getAvailableNode();
      }
      return Promise.resolve();
    };
    fetchSettings()
      .then(appSettings => {
        let prettyUrlParsed;
        let prettyUrlObj;
        if (
          !(options?.__validation__) &&
          (
            appSettings.prettyUrlDomain ||
            appSettings.prettyUrlPath
          )
        ) {
          prettyUrlParsed = generatePrettyUrl(
            appSettings.prettyUrlDomain,
            appSettings.prettyUrlPath,
            options
          );
          prettyUrlObj = prettyUrlParsed ? JSON.parse(prettyUrlParsed) : undefined;
        }
        getApplicationRun(
          application.id,
          user?.userName,
          `${application.image}:${version || 'latest'}`,
          options.__validation__ ? undefined : appOwner,
          appSettings.checkRunPrettyUrl && prettyUrlObj && !(options?.__skip_pretty_url_check__)
            ? `${prettyUrlObj?.domain || ''};${prettyUrlObj?.path || ''}`
            : undefined,
          options?.__check_run_parameters__
        )
          .then(userRuns => {
            // find user run
            const run = findUserRun(
              userRuns,
              appSettings,
              user,
              options
            );
            if (run) {
              launches.set(runIdKey, run.id);
              pollRun(run, resolve, handleReject, false, appSettings);
            } else {
              getToolSettings(application.id, version)
                .then(settings => {
                  getAvailableNodeWrapper(appSettings)
                    .then(node => {
                      if (settings.useParentNodeId) {
                        if (node) {
                          console.log(`Application will be launched with parent node id ${node.runId}; node ${node.name}`);
                        } else {
                          console.log('Node not found. Application will be launched without parent node id');
                        }
                      } else {
                        console.log('Application will be launched without parent node id');
                      }
                      getAppOwnerInfo()
                        .then(owner => {
                          parseGatewaySpec(appSettings, options, owner, user)
                            .then(gatewaySpec => {
                              if (!applicationAvailable(gatewaySpec, user, appSettings)) {
                                handleReject(new Error(`Access denied`));
                                return;
                              }
                              const joinedLaunchOptions = joinLaunchOptions(
                                appSettings,
                                options,
                                gatewaySpec
                              );
                              console.log('joined launch options:', joinedLaunchOptions);
                              const payload = Object.assign({}, settings);
                              if (settings.useParentNodeId && node && node.runId) {
                                payload.parentNodeId = node.runId;
                              }
                              if (!payload.parameters) {
                                payload.parameters = {};
                              }
                              if (prettyUrlParsed) {
                                payload.prettyUrl = prettyUrlParsed;
                                payload.parameters.RUN_PRETTY_URL = {
                                  type: 'string',
                                  value: `${prettyUrlObj?.domain || ''};${prettyUrlObj?.path || ''}`
                                };
                                console.log(`Pretty url: ${payload.prettyUrl}`);
                              }
                              console.log(`Limit mounts: "${appSettings.limitMounts}"`);
                              const attachMounts = (initial) => {
                                const result = new Set((initial || []).map(s => +s).filter(i => !Number.isNaN(i)));
                                if (user?.storages) {
                                  console.log('Limit Mounts: attaching user storages:', user?.storages);
                                  (user?.storages || []).forEach(storage => result.add(storage));
                                }
                                if (gatewaySpec?.limitMounts) {
                                  console.log('Limit Mounts: attaching mounts from gateway spec:', gatewaySpec?.limitMounts);
                                  (gatewaySpec?.limitMounts || []).forEach(storage => result.add(storage));
                                }
                                if (options?.__mounts__) {
                                  console.log('Limit Mounts: attaching storages:', options?.__mounts__);
                                  (options?.__mounts__ || []).forEach(storage => result.add(storage));
                                }
                                if (options?.sensitiveMounts) {
                                  console.log('Limit Mounts: attaching sensitive storages:', options?.sensitiveMounts);
                                  (options?.sensitiveMounts || []).forEach(storage => result.add(storage));
                                }
                                return Array.from(result).join(',');
                              };
                              if (/^default$/i.test(appSettings.limitMounts)) {
                                if (owner) {
                                  if (!owner.defaultStorageId) {
                                    handleReject(new Error(`Default data storage is not set for user ${owner?.userName}`));
                                    return;
                                  } else {
                                    payload.parameters.CP_CAP_LIMIT_MOUNTS = {
                                      type: 'string',
                                      value: attachMounts([owner?.defaultStorageId])
                                    };
                                    payload.parameters.DEFAULT_STORAGE_USER = {
                                      type: 'string',
                                      value: owner?.userName
                                    };
                                  }
                                } else if (!user?.defaultStorageId) {
                                  handleReject(new Error(`Default data storage is not set for user ${user?.userName}`));
                                  return;
                                } else {
                                  payload.parameters.CP_CAP_LIMIT_MOUNTS = {
                                    type: 'string',
                                    value: attachMounts([user?.defaultStorageId])
                                  };
                                  payload.parameters.DEFAULT_STORAGE_USER = {
                                    type: 'string',
                                    value: user?.userName
                                  };
                                }
                              } else if (/^all$/i.test(appSettings.limitMounts)) {
                                payload.parameters.CP_CAP_LIMIT_MOUNTS = undefined;
                              } else if (appSettings.limitMounts) {
                                console.log('CP_CAP_LIMIT_MOUNTS:', appSettings.limitMounts);
                                const {
                                  result: parsed,
                                  replacements = []
                                } = parseLimitMounts(
                                  appSettings.limitMounts,
                                  owner,
                                  user,
                                  joinedLaunchOptions.limitMountsPlaceholders
                                );
                                console.log('CP_CAP_LIMIT_MOUNTS parsed:', parsed);
                                console.log('CP_CAP_LIMIT_MOUNTS placeholder substitutions:', replacements);
                                if (Object.keys(replacements).length > 0) {
                                  Object.entries(replacements)
                                    .forEach(([key, value]) => {
                                      payload.parameters[key] = {
                                        type: 'string',
                                        value
                                      };
                                    });
                                }
                                payload.parameters.CP_CAP_LIMIT_MOUNTS = {
                                  type: 'string',
                                  value: attachMounts((parsed || '').split(','))
                                };
                              }
                              const params = generateParameters(appSettings?.parameters, options);
                              if (Object.keys(params).length > 0) {
                                console.log('Parameters to attach:', params);
                              }
                              payload.parameters = attachParameters(
                                payload.parameters,
                                params
                              );
                              payload.parameters = attachParameters(
                                payload.parameters,
                                options.__parameters__ || {}
                              )
                              if (
                                appSettings?.isAnonymous &&
                                appSettings?.originalUserName &&
                                appSettings?.anonymousAccess?.originalUserNameParameter
                              ) {
                                console.log(
                                  `Setting parameter "${appSettings?.anonymousAccess?.originalUserNameParameter}"="${appSettings?.originalUserName}"`
                                )
                                payload.parameters[appSettings?.anonymousAccess?.originalUserNameParameter] = {
                                  value: appSettings?.originalUserName,
                                  type: 'string'
                                };
                              }
                              if (joinedLaunchOptions.instance_size) {
                                console.log(`${joinedLaunchOptions.instance_size} instance will be used`);
                                payload.instance_size = joinedLaunchOptions.instance_size;
                              }
                              launchToolRequest(
                                application.id,
                                `${application.image}:${version || 'latest'}`,
                                payload
                              )
                                .then(launchedRun => {
                                  if (launchedRun) {
                                    console.log(`Run #${launchedRun.id} launched`);
                                    launches.set(runIdKey, launchedRun.id);
                                    shareRun(launchedRun.id)
                                      .then()
                                      .catch((e) => {
                                        console.log('Error sharing run with users/groups:', e.message);
                                      })
                                      .then(() => {
                                        pollRun(launchedRun, resolve, handleReject, true, appSettings);
                                      });
                                  } else {
                                    handleReject(new Error('Error launching tool: unknown run id (empty response from /run)'));
                                  }
                                })
                                .catch(handleReject);
                            })
                            .catch(handleReject);
                        })
                        .catch(handleReject)
                    })
                    .catch(handleReject);
                })
                .catch(handleReject);
            }
          })
          .catch(handleReject);
      });
  });
}

export default function launchApplication(application, user, options) {
  if (!CPAPI) {
    const {id} = application;
    return apiGet(`application/${id}/${user.userName}/launch`);
  } else {
    // Cloud Pipeline API:
    const runIdKey = options?.__run_id_key__ || `${application.id}-${user.userName}`;
    if (!options?.__validation__ && launches.has(runIdKey)) {
      const runId = launches.get(runIdKey);
      if (/^pending$/i.test(runId)) {
        return Promise.resolve({status: 'pending'});
      }
      return new Promise((resolve, reject) => {
        fetchSettings()
          .then(appSettings => {
            getRun(runId)
              .then(result => {
                const {status: requestStatus, message, payload: run} = result;
                if (requestStatus === 'OK') {
                  pollRun(run, resolve, reject, false, appSettings);
                } else {
                  reject(new Error(message || `Error launching configuration: status ${requestStatus}`));
                }
              })
              .catch(reject);
          });
      });
    } else {
      launches.set(runIdKey, 'pending');
      if (TOOLS) {
        // Cloud Pipeline API (tools):
        return launchTool(application, user, options);
      } else {
        // Cloud Pipeline API (configurations):
        return launchConfiguration(application, user, options);
      }
    }
  }
}
