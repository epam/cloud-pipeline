import parseLimitMounts from './limit-mounts-parser';
import { getRunPorts, parsePorts } from "./utilities/ports";

class StopRunsError extends Error {
  constructor(runs = [], description, options) {
    const runsIds = runs.map(run => `#${run.id}`).join(', ');
    super(
      `${description}${description ? '. ' : ''}You should stop ${runsIds} run${runs.length > 1 ? 's' : ''}`
    );
    this.options = options;
    this.runs = runs;
  }
}

export {StopRunsError};

function filterByNodeSize (instance) {
  return function filter (run) {
    return !instance || run?.instance?.nodeType === instance;
  }
}

function filterByPlaceholder (name, value) {
  return function filter (run) {
    return !!run &&
      !!run.pipelineRunParameters &&
      !!run.pipelineRunParameters.find(p => p.name === name && `${p.value || ''}` === `${value || ''}`);
  }
}

export function findUserRun (runs, appSettings, user, options) {
  if (options.__validation__) {
    return undefined;
  }
  if (options?.__launch__) {
    // skip check
    console.log('Skipping library check');
    const [run] = runs || [];
    return run;
  }
  if (runs.length > 0) {
    const {
      result: parsed,
      replacements = []
    } = parseLimitMounts(
      appSettings.limitMounts,
      undefined,
      user,
      options.limitMountsPlaceholders
    );
    const instance = appSettings?.appConfigNodeSizes && options?.nodeSize
      ? appSettings.appConfigNodeSizes[options?.nodeSize]
      : undefined;
    let matchedRuns = runs.filter(filterByNodeSize(instance));
    if (!matchedRuns.length) {
      throw new StopRunsError(
        runs,
        `There ${runs.length > 1 ? 'are' : 'is an'} already running job${runs.length > 1 ? 's' : ''} with the different node size`,
        {nodeSize: true}
      );
    }
    const errorOptions = {
      placeholders: []
    };
    Object.entries(replacements || {}).forEach(([name, value]) => {
      if (matchedRuns.length > 0) {
        matchedRuns = matchedRuns.filter(filterByPlaceholder(name, value));
        if (
          matchedRuns.length === 0
        ) {
          if (
            appSettings.limitMountsPlaceholders &&
            appSettings.limitMountsPlaceholders[name] &&
            appSettings.limitMountsPlaceholders[name].title
          ) {
            errorOptions.placeholders.push(appSettings.limitMountsPlaceholders[name].title);
          } else {
            errorOptions.placeholders.push(name);
          }
        }
      }
    });
    if (!matchedRuns.length) {
      throw new StopRunsError(
        runs,
        `There ${runs.length > 1 ? 'are' : 'is an'} already running job${runs.length > 1 ? 's' : ''} with different libraries`,
        errorOptions
      );
    }
    const ports = parsePorts(options?.specifyPorts || '').sort((a, b) => a - b);
    matchedRuns = matchedRuns.filter(aRun => {
      const runPorts = getRunPorts(aRun);
      if (runPorts.length === ports.length) {
        for (let i = 0; i < runPorts.length; i++) {
          if (runPorts[i] !== ports[i]) {
            return false;
          }
        }
        return true;
      }
      return false;
    });
    if (!matchedRuns.length) {
      throw new StopRunsError(
        runs,
        `There ${runs.length > 1 ? 'are' : 'is an'} already running job${runs.length > 1 ? 's' : ''} with different ports`,
        errorOptions
      );
    }
    return matchedRuns[0];
  }
  return undefined;
}
