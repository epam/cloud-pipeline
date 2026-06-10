const CP_RUN_ENGINE_TYPE = 'CP_RUN_ENGINE_TYPE';

function getRunEngineType(run) {
  if (!run) {
    return false;
  }
  const {pipelineRunParameters = []} = run;
  const cpRunEngineType = pipelineRunParameters.find((prp) => prp.name === CP_RUN_ENGINE_TYPE);
  return cpRunEngineType ? cpRunEngineType.value : undefined;
}

function checkRunEngine(run, engine) {
  const cpRunEngineType = getRunEngineType(run);
  return cpRunEngineType ? engine.toLowerCase() === cpRunEngineType.toLowerCase() : false;
}

export function isNextflowEngine(run) {
  return checkRunEngine(run, 'nextflow');
}

export function isMlflowEngine(run) {
  return checkRunEngine(run, 'mlflow');
}

export function isRunCompleted(run) {
  const {status} = run || {};
  return ['STOPPED', 'FAILURE', 'SUCCESS'].includes(status);
}

const NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE = 'No data available.';
const NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE =
  'No data available. Status information is not synchronized yet.';

export {NO_DATA_AVAILABLE_COMPLETED_JOB_MESSAGE, NO_DATA_AVAILABLE_RUNNING_JOB_MESSAGE};
