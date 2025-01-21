class CloudPipelineRunEngineTask(object):

    def __init__(self, run_id, task_group, task_id, task_key, task_name, task_tag, engine_type,
                 status, attributes, start_timestamp=None, end_timestamp=None):
        self.runId = run_id
        self.taskGroup = task_group
        self.taskId = task_id
        self.taskKey = task_key
        self.taskName = task_name
        self.taskTag = task_tag
        self.engineType = engine_type
        self.status = status
        self.attributes = attributes
        self.startDateTime = start_timestamp
        self.endDateTime = end_timestamp

    def to_dict(self):
        return vars(self)
