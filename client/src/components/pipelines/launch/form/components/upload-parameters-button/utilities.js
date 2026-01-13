import {load as loadYaml} from 'js-yaml';
import displaySize from '../../../../../../utils/displaySize';
import {
  generateParameterConfigsFromJsonPayload, parameterConfigsToPayloadConfig, parametersToPayloadParams
} from '../../utilities/parameter-utilities';

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
    // normalization
    return parameterConfigsToPayloadConfig(
      generateParameterConfigsFromJsonPayload(parsed) || []
    );
  }
  throw new Error('unsupported content format');
}
