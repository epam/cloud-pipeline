import getMetadata from "./metadata";

function parseDependency(key, dependency = {}) {
  const optionsKeys = Object.keys(dependency || {});
  if (optionsKeys.length > 0) {
    const clearOption = o => {
      const {default: _, ...rest} = o || {};
      return rest;
    }
    const options = optionsKeys.map(key => ({
      value: key,
      name: key,
      default: !!dependency[key].default,
      parameters: clearOption(dependency[key])
    }))
    const _default = options.find(o => o.default) || options[0];
    return {
      key,
      options,
      default: _default ? _default.value : 0
    };
  }
  return undefined;
}

function parseDependencies(dependencies = {}) {
  const keys = Object.keys(dependencies);
  return keys.map(key => parseDependency(key, dependencies[key])).filter(Boolean);
}

export default function fetchPlaceholderTagsDependencies(settings, storageIdentifiers) {
  if (!settings || !settings.placeholderDependenciesTagName) {
    return Promise.resolve({});
  }
  return new Promise((resolve) => {
    getMetadata(
      storageIdentifiers.map(id => ({
        entityId: id,
        entityClass: 'DATA_STORAGE'
      }))
    )
      .then(result => {
        const {
          status,
          payload,
          message
        } = result;
        if (/^ok$/i.test(status)) {
          const safeParseJson = o => {
            try {
              return JSON.parse(o);
            } catch (_) {}
            return undefined;
          };
          const filtered = (payload || [])
            .filter(item => item.data &&
              item.entity &&
              item.entity.entityId &&
              Object.prototype.hasOwnProperty.call(item.data, settings.placeholderDependenciesTagName) &&
              Object.prototype.hasOwnProperty.call(item.data[settings.placeholderDependenciesTagName], 'value')
            )
            .map(item => ([
              item.entity.entityId,
              parseDependencies(
                safeParseJson(
                  item.data[settings.placeholderDependenciesTagName].value
                )
              )
            ]))
            .filter(o => !!o[1])
            .map(o => ({[o[0]]: o[1]}))
            .reduce((r, c) => ({...r, ...c}), {});
          resolve(filtered);
          return;
        }
        throw new Error(message || '');
      })
      .catch((e) => {
        console.warn(
          `Error fetching storages ${storageIdentifiers.map(o => `#${o}`).join(', ')} metadata: ${e.message}`
        );
        resolve({});
      })
  });
}
