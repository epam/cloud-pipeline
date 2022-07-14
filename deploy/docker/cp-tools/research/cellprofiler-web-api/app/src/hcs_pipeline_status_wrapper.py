from .hcs_pipeline import HcsPipeline


class Status:
    RUNNING = 'running'
    FAILED = 'failed'
    SUCCESS = 'success'


class Task:

    def __init__(self):
        self.status = None
        self.response_data = {}
        self.error = None

    def running(self):
        self.status = Status.RUNNING

    def success(self, data):
        self.status = Status.SUCCESS
        self.response_data = data

    def failure(self, message):
        self.status = Status.FAILED
        self.error = message

    def to_json(self):
        result = {
            'response': self.response_data
        }
        if self.status:
            result.update({'status': self.status})
        if self.error:
            result.update({'error': self.status})
        return result


class HcsPipelineStatusWrapper:

    def __init__(self, measurement_uuid):
        self.pipeline = HcsPipeline(measurement_uuid)
        self.pipeline_task = None
        self.module_tasks = dict()

    def get_id(self):
        return self.pipeline.get_id()

    def run_pipeline(self):
        self.pipeline_task = Task()
        self.pipeline_task.running()
        try:
            self.pipeline.run_pipeline()
            response_data = self.pipeline.get_structure()
            self.pipeline_task.success(response_data)
        except BaseException as e:
            self.pipeline_task.error()
            raise e

    def get_pipeline(self):
        return self.pipeline.get_structure()

    def run_module(self, module_num):
        module_task = Task()
        module_task.running()
        self.module_tasks.update({module_num: module_task})
        try:
            self.pipeline.run_module(module_num)
            response_data = self.pipeline.get_structure()
            module_task.success(response_data)
        except BaseException as e:
            module_task.failure(e.__str__())
            raise e

    def add_module(self, module_name, module_pos, module_config):
        self.pipeline.add_module(module_name, module_pos, module_config)

    def edit_module(self, module_num, new_config):
        self.pipeline.edit_module(module_num, new_config)

    def move_module(self, module_num, direction):
        self.pipeline.move_module(module_num, direction)

    def remove_module(self, module_num):
        self.pipeline.remove_module(module_num)

    def set_input(self, coordinates):
        self.pipeline.set_input(coordinates)

    def get_pipeline_status(self):
        return self.pipeline_task

    def get_module_status(self, module_id):
        module_task = self.module_tasks.get(module_id)
        if not module_task:
            raise RuntimeError("No running module '%s' found" % module_id)
        return module_task
