class CloudPipelineRunEngineTask(object):

    def __init__(self, run_id, engine_run_id, engine_run_name, parent_id,
                 task_id, task_key, task_name, status, attributes,
                 start_timestamp=None, end_timestamp=None):
        self.run_id = run_id
        self.engine_run_id = engine_run_id
        self.engine_run_name = engine_run_name
        self.parent_id = parent_id
        self.task_id = task_id
        self.task_key = task_key
        self.task_name = task_name
        self.status = status
        self.attributes = attributes
        self.start_timestamp = start_timestamp
        self.end_timestamp = end_timestamp
