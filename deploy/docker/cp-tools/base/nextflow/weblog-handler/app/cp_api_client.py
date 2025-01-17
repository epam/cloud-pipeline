from pipeline import PipelineAPI

class CloudPipelineApi(object):

    def __init__(self, api, run_id):
        self.api_client = PipelineAPI(api, 'logs')
        self.run_id = run_id

    def log_pipeline_run_engine_task_events(self, events):
        try:
            if self.api_client.log_pipeline_run_engine_task_events(self.run_id, "NEXTFLOW", events):
                return True
        except Exception as e:
            for e in events:
                print("Failed to log engine task: {} {} {} {}".format(e.runId, e.taskId, e.status, e.startTimestamp))
        return False
