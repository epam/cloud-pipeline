import type { Pipeline, Project } from '@cloud-pipeline/core';

export function clonedPipelinePrefix(project: Project) {
  return `${project.name}-`;
}

export function omitClonedPipelinePrefix(
  pipeline: Pipeline,
  project?: Project,
) {
  if (!project) {
    return pipeline.name;
  }
  const prefix = clonedPipelinePrefix(project);
  if (pipeline.name.startsWith(prefix)) {
    return pipeline.name.substring(prefix.length);
  }
  return pipeline.name;
}

export function clonedPipelineName(pipeline: Pipeline, project: Project) {
  return `${clonedPipelinePrefix(project)}${pipeline.name}`;
}
