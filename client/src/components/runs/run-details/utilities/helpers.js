const CP_RUN_ENGINE_TYPE = 'CP_RUN_ENGINE_TYPE';

export function isNextflowEngine (run) {
  if (!run) {
    return false;
  }
  const {
    pipelineRunParameters = []
  } = run;
  const cpRunEngineType = pipelineRunParameters.find((prp) => prp.name === CP_RUN_ENGINE_TYPE);
  return cpRunEngineType && cpRunEngineType.value
    ? /^nextflow$/i.test(cpRunEngineType.value)
    : false;
}

export function isRunCompleted (run) {
  const {
    status
  } = run || {};
  return ['STOPPED', 'FAILURE', 'SUCCESS'].includes(status);
}

const NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE = 'No data available.';
// eslint-disable-next-line max-len
const NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE = 'No data available. Status information is not synchronized yet.';

export {
  NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE,
  NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE
};
