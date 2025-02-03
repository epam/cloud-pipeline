import type { Run } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import { useProjectById } from '../../state/projects/hooks.ts';

export function useRunDisplayName(run: Run | undefined, prefix?: string) {
  const { projectId, pipelineName, id, tags } = run ?? {};
  const project = useProjectById(projectId);
  return useMemo(() => {
    if (id) {
      const {alias} = tags ?? {};
      if (alias) {
        return `${alias} (#${id})`;
      }
      if (pipelineName) {
        let name = pipelineName;
        if (
          project &&
          name.toLowerCase().startsWith(`${project.name}-`.toLowerCase())
        ) {
          name = name.slice(project.name.length + 1);
        }
        return `${name} (#${id})`;
      }
      return prefix ? `${prefix} #${id}` : `#${id}`;
    }
    return undefined;
  }, [project, pipelineName, id, tags, prefix]);
}
