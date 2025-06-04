import preferences from '../../../../models/preferences/PreferencesLoad';
import {
  applyCustomCapabilitiesParameters
} from '../../../pipelines/launch/form/utilities/run-capabilities';
import PipelineRunCmd from '../../../../models/pipelines/PipelineRunCmd';

export async function fetchRunCliCommands (run, payload = undefined) {
  let runPayload = payload;
  if (!runPayload) {
    runPayload = await fetchRunPayload(run);
  }
  const requestLinux = new PipelineRunCmd();
  const requestWindows = new PipelineRunCmd();
  const requestCommonPayload = {
    pipelineStart: runPayload,
    quite: false,
    yes: true,
    showParams: false,
    sync: false
  };
  await Promise.all([
    requestLinux.send({...requestCommonPayload, runStartCmdExecutionEnvironment: 'LINUX'}),
    requestWindows.send({...requestCommonPayload, runStartCmdExecutionEnvironment: 'WINDOWS'})
  ]);
  if (requestWindows.error || requestLinux.error) {
    throw new Error(requestWindows.error || requestLinux.error);
  }
  return {
    linux: requestLinux.value || '',
    windows: requestWindows.value || ''
  };
}

export async function fetchRunPayload (run) {
  await preferences.fetchIfNeededOrWait();
  if (run && preferences.loaded) {
    const payload = {
      instanceType: undefined,
      hddSize: undefined,
      timeout: run.timeout,
      cmdTemplate: run.cmdTemplate,
      nodeCount: run.nodeCount,
      dockerImage: run.dockerImage,
      pipelineId: run.pipelineId,
      version: run.version,
      params: {},
      isSpot: preferences.useSpot,
      cloudRegionId: undefined,
      prettyUrl: run.prettyUrl,
      nonPause: run.nonPause,
      configurationName: run.configName,
      executionEnvironment: undefined
    };
    if (run.instance) {
      payload.instanceType = run.instance.nodeType;
      payload.hddSize = run.instance.nodeDisk;
      payload.isSpot = run.instance.spot;
      payload.cloudRegionId = run.instance.cloudRegionId;
    }
    if (run.executionPreferences) {
      payload.executionEnvironment = run.executionPreferences.environment;
    }
    if (run.pipelineRunParameters) {
      for (let i = 0; i < run.pipelineRunParameters.length; i++) {
        const param = run.pipelineRunParameters[i];
        if (param.name && param.value) {
          payload.params[param.name] = {
            value: param.value,
            type: param.type,
            enum: param.enum
          };
        }
      }
    }
    run.params = applyCustomCapabilitiesParameters(run.params, preferences);
    return payload;
  }
  return null;
}
