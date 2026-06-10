import Remote from '../basic/Remote';

class GetRunEngineTasksStats extends Remote {
  constructor(runId, engineType) {
    super();
    this.url = `/run/${runId}/engine/${engineType}/tasks/stats`;
  }
}

export default GetRunEngineTasksStats;
