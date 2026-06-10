import {ApiBaseRequestOptions, ApiServiceLogLevel, ApiServiceOptions} from './types.ts';
import BaseApiService from './base/api-service.ts';

class CloudPipelineApiService extends BaseApiService<ApiServiceOptions, ApiBaseRequestOptions> {
  constructor(logLevel = ApiServiceLogLevel.info) {
    super('cloud-pipeline api', logLevel);
  }
}

const cloudPipelineApi = new CloudPipelineApiService();

export default cloudPipelineApi;
