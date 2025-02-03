import type { Pipeline } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import { useProjectById } from '../../state/projects/hooks.ts';

export function usePipelineDisplayName(pipeline: Pipeline | undefined) {
  const { name, parentFolderId } = pipeline ?? {};
  const project = useProjectById(parentFolderId);
  return useMemo(() => {
    let result = name;
    if (result) {
      if (
        project &&
        result.toLowerCase().startsWith(`${project.name}-`.toLowerCase())
      ) {
        result = result.slice(project.name.length + 1);
      }
      return result;
    }
    return undefined;
  }, [project, name]);
}
