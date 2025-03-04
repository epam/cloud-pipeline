import type { Pipeline, PipelineVersionParameters } from '@cloud-pipeline/core';
import { useLoadableState } from '../../../shared/hooks';
import { fetchPipelineFileByPath } from '@cloud-pipeline/api';

async function fetchPipelineFileByPathWrapped(
  id: number | undefined,
  version: string | undefined,
  path: string | undefined,
): Promise<string | undefined> {
  if (id === undefined || version === undefined || path === undefined) {
    return undefined;
  }
  return fetchPipelineFileByPath(id, version, path);
}

function getPathToMainFile(pipeline?: Pipeline, versionParameters?: PipelineVersionParameters): string | undefined {
  if (!pipeline || !versionParameters?.main_file) {
    return undefined;
  }
  let codePath = pipeline?.codePath ?? '';
  if (codePath.startsWith('/')) {
    codePath = codePath.slice(1);
  }
  if (codePath.endsWith('/')) {
    codePath = codePath.slice(0, -1);
  }
  const filePathParts = versionParameters.main_file.split('.');
  if (filePathParts[filePathParts.length - 1].toLowerCase() === 'cwl') {
    return `${codePath}/${versionParameters.main_file}`;
  }
  return `${codePath}/${pipeline.name}.cwl`;
}

export const usePipelineMainFile = (
  pipeline?: Pipeline,
  version?: string,
  versionParameters?: PipelineVersionParameters,
) => {
  const path = getPathToMainFile(pipeline, versionParameters);
  const {
    state: mainFile,
    pending,
    error,
  } = useLoadableState(fetchPipelineFileByPathWrapped, pipeline?.id, version, path);
  return { mainFile, pending, error };
};
