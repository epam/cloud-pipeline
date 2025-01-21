from pipeline import PipelineAPI

class CloudPipelineApi(object):

    def __init__(self, api, run_id, logger):
        self.logger = logger
        self.api_client = PipelineAPI(api, 'logs')
        self.run_id = run_id

    def log_pipeline_run_engine_task_events(self, events):
        try:
            event_dict_list = [e.to_dict() for e in events]
            if self.api_client.log_pipeline_run_engine_task_events(self.run_id, event_dict_list):
                return True
        except Exception as e:
            self.logger.error("Failed to log engine task events.")
        return False
