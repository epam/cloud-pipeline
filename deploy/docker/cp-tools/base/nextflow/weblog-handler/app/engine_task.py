class CloudPipelineRunEngineTask(object):

    def __init__(self, run_id, task_group, task_id, task_key, task_name, status, attributes,
                 start_timestamp=None, end_timestamp=None):
        self.runId = run_id
        self.taskGroup = task_group
        self.taskId = task_id
        self.taskKey = task_key
        self.taskName = task_name
        self.status = status
        self.attributes = attributes
        self.startTimestamp = start_timestamp
        self.endTimestamp = end_timestamp
