import preferences from '../../../../models/preferences/PreferencesLoad';

/**
 * @typedef {Object} ToolLaunchPayloadOptions
 * @property {Object} tool
 * @property settings
 * @property {string} [toolVersion]
 */
/**
 * @param {ToolLaunchPayloadOptions} options
 */
export function getToolLaunchPayload(options) {
  const {tool, settings = [], toolVersion: tv = 'latest'} = options;
  if (!tool) {
    throw new Error('Tool configuration not found');
  }
  const toolVersion = tv.toLowerCase();
  const versionSettings = settings.find((v) => (v.version || '').toLowerCase() === toolVersion);
  const defaultVersionSettings = settings.find((v) => (v.version || '').toLowerCase() === 'latest');
  const versionSettingValue = (settingName) => {
    if (
      versionSettings &&
      versionSettings.settings &&
      versionSettings.settings.length &&
      versionSettings.settings[0].configuration
    ) {
      return versionSettings.settings[0].configuration[settingName];
    }
    if (
      defaultVersionSettings &&
      defaultVersionSettings.settings &&
      defaultVersionSettings.settings.length &&
      defaultVersionSettings.settings[0].configuration
    ) {
      return defaultVersionSettings.settings[0].configuration[settingName];
    }
    return null;
  };
  const parameterIsNotEmpty = (parameter, additionalCriteria) =>
    parameter !== null &&
    parameter !== undefined &&
    `${parameter}`.trim().length > 0 &&
    (!additionalCriteria || additionalCriteria(parameter));
  const image = `${tool.registry}/${tool.image}`;
  return {
    cmd_template: versionSettingValue('cmd_template') || tool.defaultCommand,
    docker_image: toolVersion ? `${image}:${toolVersion}` : image,
    friendly_url: versionSettingValue('friendly_url') || tool.friendly_url,
    instance_disk: +versionSettingValue('instance_disk') || tool.disk,
    instance_size: versionSettingValue('instance_size') || tool.instanceType,
    is_spot: versionSettingValue('is_spot'),
    parameters: versionSettingValue('parameters'),
    node_count: parameterIsNotEmpty(versionSettingValue('node_count'))
      ? +versionSettingValue('node_count')
      : undefined,
    cloudRegionId: parameterIsNotEmpty(versionSettingValue('cloudRegionId'))
      ? versionSettingValue('cloudRegionId')
      : undefined,
    notifications: versionSettingValue('notifications') || [],
  };
}

/**
 * @typedef {Object} RunLaunchPayloadOptions
 * @property {Object} run
 * @property {Object} [configuration]
 * @property {Object} [preferences]
 */
/**
 * @param {RunLaunchPayloadOptions} options
 */
export function getRunLaunchPayload(options) {
  const {run, configuration, preferences: prefs = preferences} = options || {};
  const getPipelineParameter = (parameterName) => {
    if (configuration && configuration.parameters) {
      for (const key in configuration.parameters) {
        if (Object.hasOwn(configuration.parameters, key) && parameterName === key) {
          return configuration.parameters[key];
        }
      }
    }
    return null;
  };
  const parameters = {
    cmd_template: run.cmdTemplate,
    docker_image: run.dockerImage,
    is_spot: prefs.useSpot,
  };
  if (run.instance) {
    parameters.instance_size = run.instance.nodeType;
    parameters.instance_disk = run.instance.nodeDisk;
    parameters.is_spot = run.instance.spot;
    parameters.cloudRegionId = run.instance.cloudRegionId;
  }
  parameters.parameters = {};
  if (run.pipelineRunParameters) {
    for (let i = 0; i < run.pipelineRunParameters.length; i++) {
      const param = run.pipelineRunParameters[i];
      if (param.name && param.value) {
        const parameterInfo = getPipelineParameter(param.name);
        const type = param.type
          ? param.type
          : parameterInfo && parameterInfo.type
            ? parameterInfo.type
            : 'string';
        const required = parameterInfo && parameterInfo.required ? parameterInfo.required : false;
        parameters.parameters[param.name] = {
          ...(parameterInfo || {}),
          value: param.value,
          resolvedValue: param.resolvedValue,
          type,
          required,
          enum: param.enum,
        };
      }
    }
  }
  return parameters;
}
