import parseUserDefinedLaunchOptions from './parse-user-defined-launch-options';

/**
 * Returns {isntance_size, limitMountsPlaceholders: {placeholder: value}}; user-defined options have highest priority
 * @param appSettings
 * @param userDefinedOptions
 * @param gatewaySpecOptions
 * @return {any}
 */
export default function joinLaunchOptions (appSettings, userDefinedOptions, gatewaySpecOptions) {
  const userDefinedOptionsParsed = parseUserDefinedLaunchOptions(appSettings, userDefinedOptions);
  const priority = userDefinedOptions?.__launch__
    ? [
      userDefinedOptionsParsed?.limitMountsPlaceholders || {},
      gatewaySpecOptions?.limitMountsPlaceholders || {}
    ]
    : [
      gatewaySpecOptions?.limitMountsPlaceholders || {},
      userDefinedOptionsParsed?.limitMountsPlaceholders || {}
    ];
  const limitMountsPlaceholders = Object.assign(
    {},
    ...priority,
  );
  return Object.assign(
    {},
    gatewaySpecOptions || {},
    userDefinedOptionsParsed,
    {
      limitMountsPlaceholders
    }
  )
}
