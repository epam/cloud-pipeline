import RemotePost from '../basic/RemotePost';

class GetRunEngineTasksFilter extends RemotePost {
  constructor(runId, engineType) {
    super();
    this.url = `/run/${runId}/engine/${engineType}/tasks/filter`;
  }
}

export default GetRunEngineTasksFilter;
