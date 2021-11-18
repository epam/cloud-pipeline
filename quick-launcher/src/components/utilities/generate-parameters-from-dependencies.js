import readOptionsValue from './read-options-value';

function parseDependency(dependency, config) {
  const result = {};
  console.log('[PARSING PLACEHOLDER DEPENDENCIES] Parsing dependencies values:', dependency, 'with configuration', config);
  Object.entries(dependency || {})
    .filter(([key]) => !/^__.+__$/i.test(key))
    .forEach(([key, value]) => {
      const dependencyConfig = (config || [])
        .find(c => c.key === key);
      if (dependencyConfig) {
        const {options = []} = dependencyConfig;
        const option = options.find(o => o.value === value);
        console.log(`[PARSING PLACEHOLDER DEPENDENCIES] Dependency config for ${key} ${value}:`, option);
        if (option && option.parameters) {
          Object.entries(option.parameters)
            .forEach(([parameterName, parameterValue]) => {
              console.log(`[PARSING PLACEHOLDER DEPENDENCIES] Parameter "${parameterName}" with value "${parameterValue}" will be used`);
              result[parameterName] = {
                type: 'string',
                value: parameterValue
              }
            })
        }
      } else {
        console.warn(`[PARSING PLACEHOLDER DEPENDENCIES] Dependency config not found for "${key}"`);
      }
    });
  return result;
}

export default function generateParametersFromDependencies(
  placeholdersSettings,
  launchExtendedOptions,
  configuration
) {
  console.log('[PARSING PLACEHOLDER DEPENDENCIES] Reading parameters from placeholder dependencies...');
  console.log('[PARSING PLACEHOLDER DEPENDENCIES] Launch options:');
  console.log(JSON.parse(JSON.stringify(launchExtendedOptions || {})));
  const {
    __dependencies__: dependencies
  } = launchExtendedOptions || {};
  let parameters = {};
  (placeholdersSettings || [])
    .filter(setting => setting.isPlaceholder)
    .forEach(placeholderConfig => {
      const value = readOptionsValue(launchExtendedOptions, placeholderConfig.optionsField);
      const dependenciesValue = readOptionsValue(dependencies, placeholderConfig.optionsField);
      const storage = placeholderConfig.valuePresentation ? placeholderConfig.valuePresentation(value) : value;
      console.log('[PARSING PLACEHOLDER DEPENDENCIES]',
        `Processing placeholder "${placeholderConfig.name}":`,
        `placeholder value is "${storage}" (#${value}), dependencies:`,
        Object.entries(dependenciesValue || {})
          .filter(([key]) => !/^__.+__$/i.test(key))
          .map(([key, value]) => `${key}: ${value}`)
          .join(', ')
      );
      parameters = {
        ...parameters,
        ...parseDependency(dependenciesValue, (configuration || {})[value])
      };
    });
  console.log('[PARSING PLACEHOLDER DEPENDENCIES] result', parameters);
  return parameters;
}
