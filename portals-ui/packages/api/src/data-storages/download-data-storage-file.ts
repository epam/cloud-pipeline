import cloudPipelineApi from '../cloud-pipeline-api';

type DownloadFileResponse = {
  expires: string;
  url: string;
  cannedACLValue?: string;
  tagValue?: string;
};

export async function downloadDataStorageFile(
  storageId: number,
  path: string,
  abortSignal?: AbortSignal,
): Promise<DownloadFileResponse> {
  return await cloudPipelineApi.jsonGet<DownloadFileResponse>({
    uri: `/datastorage/${storageId}/generateUrl?path=${path}`,
    signal: abortSignal,
  });
}
