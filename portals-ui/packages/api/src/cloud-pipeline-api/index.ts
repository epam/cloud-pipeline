import { BaseApiService } from '../base/api-service';
import { ApiBaseRequestOptions, ApiServiceOptions } from '../base/@types';
import { ApiServiceLogLevel } from '../base/log-level';

class CloudPipelineApiService extends BaseApiService<
  ApiServiceOptions,
  ApiBaseRequestOptions
> {
  constructor(logLevel = ApiServiceLogLevel.info) {
    super('cloud-pipeline api', logLevel);
  }
}

const cloudPipelineApi = new CloudPipelineApiService();

export default cloudPipelineApi;
