import {load as loadYaml} from 'js-yaml';
import displaySize from '../../../../../../utils/displaySize';

function parameterTypeFromValue (value) {
  if (typeof value === 'boolean') {
    return 'boolean';
  }
  return 'string';
}

function isCorrectValue (value) {
  return value === undefined ||
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean';
}

function extractParameterFromObject (name, obj) {
  const {
    value,
    type,
    description,
    section,
    required,
    // eslint-disable-next-line camelcase
    no_override,
    enum: enumeration,
    validation,
    // eslint-disable-next-line camelcase
    pretty_name
  } = obj;
  const asString = (o) => (typeof o === 'string' ? o : undefined);
  const v = isCorrectValue(value) ? value : undefined;
  const e = (() => {
    if (enumeration && typeof enumeration === 'object' && Array.isArray(enumeration)) {
      const f = (o) => typeof o === 'string';
      const items = enumeration.filter(f);
      return items.length > 0 ? items : undefined;
    }
    return undefined;
  })();
  const t = (() => {
    if (type && typeof type === 'string') {
      switch (asString(type) ?? 'string') {
        case 'boolean':
          return 'boolean';
        default:
          return 'string';
      }
    }
    return 'string';
  })();
  const asConditional = (o, defaultValue) => typeof o === 'boolean'
    ? o : typeof o === 'string'
      ? o
      : defaultValue;
  const cfg = {
    type: t,
    description: asString(description),
    section: asString(section),
    required: asConditional(required, v === undefined),
    no_override: asConditional(no_override, false),
    enum: e,
    value: v,
    pretty_name: asString(pretty_name),
    validation: asString(validation)
  };
  return {
    name,
    ...cfg
  };
}

function generateParametersFromJson (json) {
  if (typeof json === 'object' && !Array.isArray(json)) {
    const result = [];
    for (const [key, value] of Object.entries(json)) {
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        result.push({
          name: key,
          type: parameterTypeFromValue(value),
          value
        });
      } else if (typeof value === 'object') {
        result.push(extractParameterFromObject(key, value));
      }
    }
    return result;
  }
  return [];
}

function readFile (file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      resolve(reader.result);
    };
    reader.onerror = () => {
      reject(reader.error);
    };
    reader.readAsText(file);
  });
}

const maxFileSizeBytes = 1024 * 1024 * 5; // 5MB
export async function readParametersFile (file) {
  if (file.size > maxFileSizeBytes) {
    throw new Error(`maximum file size is ${displaySize(maxFileSizeBytes)}`);
  }
  const content = await readFile(file);
  const readAsYaml = () => {
    try {
      const yamlContent = loadYaml(content);
      if (yamlContent && typeof yamlContent === 'object' && !Array.isArray(yamlContent)) {
        return yamlContent;
      }
    } catch {
      return undefined;
    }
  };
  const readAsJson = () => {
    try {
      const jsonContent = JSON.parse(content);
      if (jsonContent && typeof jsonContent === 'object' && !Array.isArray(jsonContent)) {
        return jsonContent;
      }
    } catch {
      return undefined;
    }
  };
  const parsed = readAsYaml() ?? readAsJson();
  if (parsed) {
    const params = generateParametersFromJson(parsed) || [];
    return params.reduce((acc, cur) => {
      const {name, ...parameter} = cur;
      return {
        ...acc,
        [name]: parameter
      };
    }, {});
  }
  throw new Error('unsupported content format');
}
