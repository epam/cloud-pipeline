import RemotePost from '../basic/RemotePost';

class GetRunTaskRuntimeData extends RemotePost {
  constructor(runId) {
    super();
    this.url = `/run/${runId}/runtime/data?type=NF_TASK`;
  }
}

export default GetRunTaskRuntimeData;
