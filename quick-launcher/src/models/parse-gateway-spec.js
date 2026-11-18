import processString from './process-string';
import getDataStorageItemContent from './cloud-pipeline-api/data-storage-item-content';
import fetchMountsForPlaceholders from './parse-limit-mounts-placeholders-config';
import parseStoragePlaceholder from './parse-storage-placeholder';

export function processGatewaySpecParameters(parameters) {
  return Object.keys(parameters || {})
    .map((key) => ({ [key]: { value: parameters[key], type: 'string' }}))
    .reduce((r, c) => ({ ...r, ...c }), {});
}

function processGatewaySpec (appSettings, content, resolve, reject) {
  try {
    const json = JSON.parse(content);
    const {
      instance,
      users = [],
      mounts,
      region,
      parameters,
      ...placeholders
    } = json;
    let instance_size;
    if (
      instance &&
      appSettings.appConfigNodeSizes &&
      appSettings.appConfigNodeSizes.hasOwnProperty(instance)
    ) {
      console.log(`Reading ${appSettings.appConfigNodeSizes[instance]} node size from gateway.spec`);
      instance_size = appSettings.appConfigNodeSizes[instance];
    } else if (instance) {
      console.warn(`Unknown node size config: ${instance}`);
    }
    const placeholderValues = Object.entries(placeholders || {});
    const limitMounts = (mounts || '')
      .split(',')
      .map(o => o.trim())
      .filter(o => o && o !== '')
      .map(o => Number(o))
      .filter(n => !Number.isNaN(n));
    console.log('User-defined mounts from gateway.spec:', limitMounts);
    if (placeholderValues.length > 0) {
      fetchMountsForPlaceholders(
        Object.entries(
          appSettings?.limitMountsPlaceholders || {}
        )
          .map(([name, config]) => ({placeholder: name, config}))
      )
        .then(parsed => {
          const limitMountsPlaceholders = {};
          placeholderValues.forEach(([placeholder, value]) => {
            let storageIdentifier;
            let valueParsed = false;
            if (
              appSettings?.limitMountsPlaceholders &&
              appSettings?.limitMountsPlaceholders[placeholder]
            ) {
              storageIdentifier = appSettings?.limitMountsPlaceholders[placeholder].default;
            }
            if (parsed.hasOwnProperty(placeholder)) {
              const storage = parsed[placeholder]
                .find(s => (s.name || '').toLowerCase() === (value || '').toLowerCase());
              if (storage) {
                valueParsed = true;
                storageIdentifier = `${storage.id}`;
              }
            }
            console.log(
              `Parsing placeholder "${placeholder}" value "${value}": ${storageIdentifier} ${valueParsed ? '(parsed)' : '(default value)'}`
            );
            if (storageIdentifier) {
              limitMountsPlaceholders[placeholder] = storageIdentifier;
            }
          });
          resolve({
            instance_size,
            limitMountsPlaceholders,
            users,
            limitMounts,
            region,
            gatewaySpecParameters: processGatewaySpecParameters(parameters)
          });
        });
    } else {
      resolve({
        instance_size,
        limitMounts,
        users,
        region,
        gatewaySpecParameters: processGatewaySpecParameters(parameters)
      });
    }
  } catch (e) {
    console.warn(`Error parsing gateway.spec: ${e.message}`);
    const message = e.message || '';
    reject(new Error(`gateway.spec: ${message.slice(0, 1).toLowerCase()}${message.slice(1)}`));
  }
}

export default function parseGatewaySpec (appSettings, options, user, currentUser) {
  if (options.__gateway_spec__) {
    return new Promise((resolve, reject) =>
      processGatewaySpec(appSettings, JSON.stringify(options.__gateway_spec__), resolve, reject)
    );
  }
  return new Promise((resolve, reject) => {
    const dataStorageId = parseStoragePlaceholder(appSettings.appConfigStorage, user, currentUser);
    if (!Number.isNaN(Number(dataStorageId)) && appSettings.appConfigPath) {
      const path = processString(appSettings.appConfigPath, options);
      getDataStorageItemContent(
        dataStorageId,
        path
      )
        .then(content => {
          if (content) {
            console.log('Parsing gateway.spec:', content, `(storage: #${dataStorageId}; path: ${path})`);
            processGatewaySpec(appSettings, content, resolve, reject);
          } else {
            console.warn('gateway.spec is empty', `(storage: #${dataStorageId}; path: ${path})`);
            resolve();
          }
        })
        .catch(e => {
          console.warn(e.message);
          resolve();
        });
    } else {
      resolve();
    }
  });
}
