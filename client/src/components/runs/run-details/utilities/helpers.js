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
