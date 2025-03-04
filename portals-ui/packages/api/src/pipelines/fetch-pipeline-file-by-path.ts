import cloudPipelineApi from '../cloud-pipeline-api';

export async function fetchPipelineFileByPath(pipelineId: number, version: string, path: string) {
  return await cloudPipelineApi.textGet({
    uri: `/pipeline/${pipelineId}/file?version=${version}&path=${encodeURIComponent(path)}`,
  });
}
