import getUserRuns from './get-user-runs';

const runHasParameterWithValue = (parameter, valueRegExp) => run => (run.pipelineRunParameters || [])
  .find(p => p.name === parameter && valueRegExp.test(`${p.value}`));

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
      searchCriteriaDescriptions.push(`docker image is "${dockerImage}"`);
    }
    searchCriteriaDescriptions.push(`APPLICATION parameter is ${application}`);
    if (storageUser) {
      searchCriteriaDescriptions.push(`DEFAULT_STORAGE_USER parameter is "${storageUser}"`);
    }
    if (prettyUrl) {
      searchCriteriaDescriptions.push(`RUN_PRETTY_URL parameter is "${prettyUrlCorrected}"`);
    }
    if (parametersToCheck && !!Object.values(parametersToCheck || {}).find(o => o !== undefined)) {
      const conditions = [];
      Object.entries(parametersToCheck || {})
        .forEach(([key, value]) => {
          searchCriteriaDescriptions.push(`${key} parameter is "${value}"`);
          const regExp = new RegExp(`^${value}$`, 'i');
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
        const userRuns = runs
          .filter(run => dockerImageRegExp.test(run.dockerImage)
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
            && parametersCheck(run)
          )
        resolve(userRuns);
      })
      .catch(reject);
  });
}
