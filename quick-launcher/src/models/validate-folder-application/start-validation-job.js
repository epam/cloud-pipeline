import fetchSettings from '../base/settings';
import getApplications from '../applications';
import launchApplication from '../launch-application';
import buildGatewaySpec from './build-gateway-spec';
import { getApplicationTypeSettings } from '../folder-application-types';
import mapFolderApplication from '../map-folder-application-with-tool';

export default function startValidationJob (application, callbacks = {}) {
  const {
    log = () => {},
    percent: percentCb = () => {}
  } = callbacks;
  const progressCallback = (info, percent) => {
    log(info);
    percentCb(info, percent);
  };
  return new Promise(async (resolve) => {
    progressCallback('fetching settings...', 0);
    let error;
    let run;
    try {
      const rawSettings = await fetchSettings();
      const settings = getApplicationTypeSettings(rawSettings, application?.appType);
      progressCallback('settings fetched', 1);
      progressCallback('fetching tools...', 2);
      const {applications: allApplications = [], userInfo = {}} = await getApplications(
        {silent: true, folder: true}
      );
      const tool = mapFolderApplication(application, allApplications);
      if (!tool) {
        throw new Error(`Tool not found`);
      }
      progressCallback(`${tool.image || tool.id} tool will be used`, 5);
      progressCallback('building default gateway.spec data...', 6);
      const gatewaySpec = await buildGatewaySpec(application?.info, settings);
      progressCallback(`gateway.spec built: ${JSON.stringify(gatewaySpec)}`, 7);
      const launchUrl = new URL(application.rawUrl, document.location.href);
      const pathInfo = settings ? settings.parseUrl(launchUrl) : application.pathInfo;
      progressCallback(`application path info: ${JSON.stringify(pathInfo || {})}`, 8);
      progressCallback(
        `launching tool ${tool.image || ''} (#${tool.id})`,
        9
      );
      const jobParameters = settings?.folderApplicationValidation?.parameters || {};
      const gatewaySpecKeys = Object.keys(gatewaySpec || {})
        .filter(key => gatewaySpec[key] !== undefined)
        .sort();
      const gatewaySpecId = gatewaySpecKeys
        .map(key => `${key}:${gatewaySpec[key] || ''}`)
        .join('|')
      const optionsToLaunch = {
        ...(pathInfo || {}),
        __validation__: true,
        __run_id_key__: `${tool.id}|${userInfo.userName}|validation|${gatewaySpecId}`,
        __gateway_spec__: gatewaySpec || {},
        __check_run_parameters__: jobParameters,
        __mounts__: [application.storage],
        __parameters__: Object
          .keys(jobParameters)
          .filter(key => jobParameters[key] !== undefined)
          .map(key => ({[key]: {value: jobParameters[key]}}))
          .reduce((r, c) => ({...r, ...c}), {})
      };
      const result = await launchApplication(tool, userInfo, optionsToLaunch);
      run = result?.run;
    } catch (e) {
      error = e.message;
    } finally {
      progressCallback(!error ? 'validation job launched' : error, 100);
      resolve({error, run});
    }
  });
}
