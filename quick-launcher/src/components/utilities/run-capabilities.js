const DIND = {
  id: 'DIND',
  name: 'DinD',
  parameters: [
    'CP_CAP_DIND_CONTAINER'
  ]
};

const DCV = {
  id: 'DCV',
  name: 'NICE DCV',
  parameters: [
    'CP_CAP_DCV',
    'CP_CAP_DCV_DESKTOP',
    'CP_CAP_DCV_WEB',
    'CP_CAP_SYSTEMD_CONTAINER',
  ]
};

const CAPABILITIES = [
  DIND,
  DCV,
];

function getCapabilitiesParameters (capabilities = []) {
  return capabilities
    .map((capability) => CAPABILITIES.find((cap) => cap.id === capability))
    .filter((capability) => capability && capability.parameters && capability.parameters.length > 0)
    .map((capability) => capability.parameters)
    .reduce((all, parameters) => ([...all, ...parameters]), [])
    .reduce((result, parameter) => ({
      ...result,
      [parameter]: {
        type: 'boolean',
        value: true
      }
    }), {});
}

export {
  CAPABILITIES,
  getCapabilitiesParameters,
};
