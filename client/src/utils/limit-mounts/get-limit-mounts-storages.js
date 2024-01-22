const MatchingRules = {
  identifier: 'identifier',
  path: 'path'
};

function getStorageMatchingRuleForIdentifier (storage, lmIdentifier) {
  const {
    id,
    path
  } = storage;
  if (!Number.isNaN(Number(lmIdentifier))) {
    const match = id === Number(lmIdentifier);
    return match ? {
      rule: MatchingRules.identifier,
      value: lmIdentifier,
      storage
    } : undefined;
  }
  if (path && lmIdentifier.toLowerCase() === path.toLowerCase()) {
    return {rule: MatchingRules.path, value: lmIdentifier, storage};
  }
  return undefined;
}

/**
 * @param {Storage} storage
 * @param {string[]} lmIdentifiers
 * @returns {{rule: string, storage: Storage, value: string}|undefined}
 */
function getStorageMatchingRule (storage, lmIdentifiers = []) {
  if (storage.shared) {
    return undefined;
  }
  return lmIdentifiers
    .map((lmIdentifier) => getStorageMatchingRuleForIdentifier(storage, lmIdentifier))
    .filter((Boolean))[0];
}

/**
 * Returns true if storage matches some of the identifiers
 * @param {Storage} storage
 * @param {string[]} identifiers
 * @returns {boolean}
 */
export function storageMatchesIdentifiers (storage, identifiers = []) {
  if (identifiers.some((id) => /^none$/i.test(id))) {
    return false;
  }
  return !storage.shared &&
    identifiers.some((id) => getStorageMatchingRuleForIdentifier(storage, id));
}

export function storageMatchesIdentifiersString (storage, identifiersString) {
  const identifiers = (identifiersString || '').split(/[,;]/);
  return storageMatchesIdentifiers(storage, identifiers);
}

function getStorageRuleForIdentifier (lmIdentifier, storages = []) {
  return storages
    .filter((storage) => !storage.shared)
    .map((storage) => getStorageMatchingRuleForIdentifier(storage, lmIdentifier))
    .filter(Boolean)[0];
}

export function limitMountsValueIsNone (limitMountsString) {
  const ids = (limitMountsString || '').split(/[,;]/);
  return ids.some((id) => /^none$/i.test(id));
}

function getStoragesParsingRules (identifiers, storages = []) {
  if (identifiers.some((id) => /^none$/i.test(id))) {
    return [];
  }
  return storages
    .filter((storage) => !storage.shared)
    .map((storage) => getStorageMatchingRule(storage, identifiers))
    .filter(Boolean);
}

export function getStoragesByIdentifiers (identifiers, storages = []) {
  return getStoragesParsingRules(identifiers, storages)
    .map((rule) => rule.storage);
}

function getLimitMountsStoragesParsingRules (limitMountsString, storages = []) {
  const ids = (limitMountsString || '').split(/[,;]/);
  return getStoragesParsingRules(ids, storages);
}

export function getLimitMountsUnmappedStorageIdentifiers (limitMountsString, storages = []) {
  const ids = (limitMountsString || '').split(/[,;]/);
  if (ids.some((id) => /^none$/i.test(id))) {
    return [];
  }
  return ids.filter((id) => !getStorageRuleForIdentifier(id, storages));
}

export function getLimitMountsStorages (limitMountsString, storages = []) {
  return getLimitMountsStoragesParsingRules(limitMountsString, storages)
    .map((rule) => rule.storage);
}

export function getLimitMountsParameterValue (storages, originalParameterValue = undefined) {
  const rules = getLimitMountsStoragesParsingRules(originalParameterValue || '', storages);
  const getRuleForStorage = (storage) => rules.find((rule) => rule.storage.id === storage.id) ||
    {storage, rule: MatchingRules.identifier, value: `${storage.id}`};
  return storages.map((storage) => getRuleForStorage(storage).value).join(',');
}

/**
 * @typedef {Object} CorrectLimitMountsParameterValueOptions
 * @property {boolean} [allowSensitive=true]
 * @property {boolean} [keepUnmappedIdentifiers=false]
 */

/**
 * @param {string} limitMountsString
 * @param {Storage[]} storages
 * @param {CorrectLimitMountsParameterValueOptions} [options]
 * @returns {string|null}
 */
export function correctLimitMountsParameterValue (
  limitMountsString,
  storages = [],
  options = undefined
) {
  if (!limitMountsString || !limitMountsString.length) {
    return null;
  }
  if (limitMountsValueIsNone(limitMountsString)) {
    return 'None';
  }
  const {
    allowSensitive = true,
    keepUnmappedIdentifiers = false
  } = options || {};
  const result = getLimitMountsStoragesParsingRules(limitMountsString || '', storages)
    .filter((rule) => allowSensitive || !rule.storage.sensitive)
    .map((rule) => rule.value)
    .concat(
      keepUnmappedIdentifiers
        ? getLimitMountsUnmappedStorageIdentifiers(limitMountsString || '', storages)
        : []
    );
  if (result.length > 0) {
    return [...new Set(result)].join(',');
  }
  return null;
}
