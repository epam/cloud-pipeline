import type {
  LaunchPayload,
  Pipeline,
  PipelineConfiguration,
  PipelineParameter,
} from '@cloud-pipeline/core';

function generateLaunchPayload(
  pipeline: Pipeline,
  configuration: PipelineConfiguration,
  parameters: Record<string, PipelineParameter>,
  version: string,
): LaunchPayload {
  const {
    instance_size,
    instance_disk,
    timeout,
    cmd_template,
    docker_image,
    is_spot,
    notifications = [],
    cloudRegionId,
  } = configuration?.configuration || {};
  const payload = {
    instanceType: instance_size,
    hddSize: Number(instance_disk),
    timeout,
    cmdTemplate: cmd_template,
    dockerImage: docker_image,
    pipelineId: pipeline.id,
    version,
    params: parameters,
    isSpot: is_spot,
    notifications,
    cloudRegionId,
    configurationName: configuration.name,
    force: true,
  };
  return payload;
}

export { generateLaunchPayload };
