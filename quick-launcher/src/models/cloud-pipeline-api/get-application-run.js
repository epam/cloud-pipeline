import getUserRuns from './get-user-runs';

const runHasParameterWithValue = (parameter, valueRegExp) => run => {
  const result = (run.pipelineRunParameters || [])
    .find(p => p.name === parameter && valueRegExp.test(`${p.value}`));
  console.log(`Checking parameter "${parameter}" value:`,!!result);
  return !!result;
}

function escapeRegExpString (string) {
  let result = `${(string === undefined || string === null) ? '' : string}`;
  result = result.replace(/\+/g, '\\+');
  result = result.replace(/\./g, '\\.');
  result = result.replace(/\^/g, '\\^');
  result = result.replace(/\$/g, '\\$');
  result = result.replace(/\(/g, '\\(');
  result = result.replace(/\)/g, '\\)');
  result = result.replace(/\[/g, '\\[');
  result = result.replace(/\]/g, '\\]');
  result = result.replace(/\{/g, '\\{');
  result = result.replace(/\}/g, '\\}');
  result = result.replace(/\?/g, '\\?');
  result = result.replace(/\*/g, '\\*');
  return result;
}

export default function getApplicationRun(
  application,
  user,
  dockerImage,
  storageUser = undefined,
  prettyUrl,
  parametersToCheck = {}
) {
  const dockerImageRegExp = dockerImage ? new RegExp(`^${dockerImage}$`, 'i') : /.*/;
  const storageUserRegExp = storageUser ? new RegExp(`^${storageUser}$`, 'i') : undefined;
  const prettyUrlCorrected = prettyUrl
    ? prettyUrl.replace(/\[/g, '\\[').replace(/\]/g, '\\]')
    : undefined;
  const prettyUrlRegExp = prettyUrl
    ? new RegExp(`^${prettyUrlCorrected}$`, 'i')
    : undefined;
  return new Promise((resolve, reject) => {
    let parametersCheck = () => true;
    const searchCriteriaDescriptions = [];
    if (dockerImage) {
      searchCriteriaDescriptions.push(`docker image should be "${dockerImage}"`);
    }
    searchCriteriaDescriptions.push(`APPLICATION parameter should be ${application}`);
    if (storageUser) {
      // We're checking this parameter ALWAYS when not in validation mode;
      // However, we're setting this parameter only if settings.limitMounts === 'default'
      searchCriteriaDescriptions.push(`DEFAULT_STORAGE_USER parameter should be "${storageUser}"`);
    }
    if (prettyUrl) {
      searchCriteriaDescriptions.push(`RUN_PRETTY_URL parameter should be "${prettyUrlCorrected}"`);
    }
    if (parametersToCheck && !!Object.values(parametersToCheck || {}).find(o => o !== undefined)) {
      const conditions = [];
      Object.entries(parametersToCheck || {})
        .forEach(([key, value]) => {
          const parameterValue = value && value.value ? value.value : value;
          searchCriteriaDescriptions.push(`${key} parameter should be "${parameterValue}"`);
          const regExp = new RegExp(`^${escapeRegExpString(parameterValue)}$`, 'i');
          conditions.push(runHasParameterWithValue(key, regExp));
        });
      parametersCheck = run => conditions
        .map(condition => condition(run))
        .filter(conditionPassed => !conditionPassed)
        .length === 0;
    }
    console.log(`Searching user (${user}) jobs with following conditions:`);
    searchCriteriaDescriptions.forEach(condition => console.log(condition));
    getUserRuns(user)
      .then(runs => {
        console.log('Current user jobs:', runs);
        console.log('');
        const userRuns = runs
          .filter(run => {
            console.log(`Checking run #${run.id}:`);
            console.log(`Docker image (${run.dockerImage}) test:`, dockerImageRegExp.test(run.dockerImage));
            console.log(`APPLICATION parameter test:`, (run.pipelineRunParameters || [])
              .filter(p => p.name === 'APPLICATION' && `${p.value}` === `${application}`)
              .length > 0
            );
            if (storageUserRegExp) {
              console.log(`DEFAULT_STORAGE_USER test:`, (run.pipelineRunParameters || [])
                .filter(p => p.name === 'DEFAULT_STORAGE_USER' && storageUserRegExp.test(`${p.value}`))
                .length > 0);
            }
            if (prettyUrlRegExp) {
              console.log(`RUN_PRETTY_URL test:`, (run.pipelineRunParameters || [])
                .filter(p => p.name === 'RUN_PRETTY_URL' && prettyUrlRegExp.test(`${p.value}`))
                .length > 0);
            }
            console.log('Parameters test:', parametersCheck(run));
            const result = dockerImageRegExp.test(run.dockerImage)
              && (run.pipelineRunParameters || [])
                .filter(p => p.name === 'APPLICATION' && `${p.value}` === `${application}`)
                .length > 0
              && (
                !storageUserRegExp ||
                (run.pipelineRunParameters || [])
                  .filter(p => p.name === 'DEFAULT_STORAGE_USER' && storageUserRegExp.test(`${p.value}`))
                  .length > 0
              )
              && (
                !prettyUrlRegExp ||
                (run.pipelineRunParameters || [])
                  .filter(p => p.name === 'RUN_PRETTY_URL' && prettyUrlRegExp.test(`${p.value}`))
                  .length > 0
              )
              && parametersCheck(run);
            console.log(`Run #${run.id} test result:`, result);
            console.log('');
            return result;
          });
        console.log('User runs (filtered):', userRuns);
        console.log('');
        resolve(userRuns);
      })
      .catch(reject);
  });
}
