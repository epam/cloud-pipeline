import fetchSettings from '../base/settings';
import getApplications from '../applications';
import launchApplication from '../launch-application';
import buildGatewaySpec from './build-gateway-spec';

function wrapRequest (request, name, result = {}) {
  return new Promise((resolve, reject) => {
    request
      .then(payload => {
        resolve({
          ...result,
          [name]: payload
        })
      })
      .catch(reject);
  });
}

export default function startValidationJob (application, callbacks = {}) {
  const {
    log = () => {},
    percent: percentCb = () => {}
  } = callbacks;
  const progressCallback = (info, percent) => {
    log(info);
    percentCb(info, percent);
  };
  return new Promise((resolve) => {
    progressCallback('fetching settings...', 0);
    fetchSettings()
      .then((settings) => {
        progressCallback('settings fetched', 1);
        progressCallback('fetching tools...', 2);
        return wrapRequest(getApplications(true), 'applicationsPayload', {settings});
      })
      .then((payload) => {
        const {settings, applicationsPayload = {}} = payload;
        const {applications = [], userInfo = {}} = applicationsPayload;
        progressCallback(`${applications.length} tool${applications.length > 1 ? 's' : ''} fetched`, 4);
        const [applicationToLaunch] = applications;
        if (!applicationToLaunch) {
          throw new Error(`Tool not found`);
        }
        progressCallback(`${applicationToLaunch.image} tool will be used`, 5);
        progressCallback('building default gateway.spec data...', 6);
        return wrapRequest(
          buildGatewaySpec(application?.info, settings),
          'gatewaySpec',
          {
            settings,
            tool: applicationToLaunch,
            userInfo
          }
        )
      })
      .then((launch) => {
        const {
          settings,
          tool,
          userInfo,
          gatewaySpec = {}
        } = launch;
        progressCallback(`gateway.spec built: ${JSON.stringify(gatewaySpec)}`, 7);
        const launchUrl = new URL(application.rawUrl, document.location.href);
        const pathInfo = settings ? settings.parseUrl(launchUrl) : application.pathInfo;
        progressCallback(`application path info: ${JSON.stringify(pathInfo || {})}`, 8);
        progressCallback(
          `launching tool ${tool.image} (#${tool.id})`,
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
            .reduce((r, c) => ({...r, ...c}))
        };
        return launchApplication(tool, userInfo, optionsToLaunch);
      })
      .catch(e => {
        return Promise.resolve({error: e.message});
      })
      .then(result => {
        const {
          error,
          run
        } = result || {};
        progressCallback(!error ? 'validation job launched' : result, 100);
        resolve({error, run});
      });
  });
}
