from .config import Config
from .hcs_pipeline import ImageCoords, HcsPipeline, PipelineState
from multiprocessing import Process
from time import sleep


class HCSManager:

    def __init__(self, pipelines):
        self.pipelines = pipelines
        self.running_processes = {}
        self.queue = list()

    def create_pipeline(self, measurement_uuid):
        pipeline = HcsPipeline(measurement_uuid)
        pipeline_id = pipeline.get_id()
        self.pipelines.update({pipeline_id: pipeline})
        return pipeline_id

    def get_pipeline(self, pipeline_id):
        pipeline = self._get_pipeline(pipeline_id)
        return pipeline.get_structure()

    def add_files(self, pipeline_id, files_data):
        pipeline = self._get_pipeline(pipeline_id)
        coordinates_list = list()
        for coordinates in files_data:
            x = int(self._get_required_field(coordinates, 'x'))
            y = int(self._get_required_field(coordinates, 'y'))
            z = int(self._get_required_field(coordinates, 'z'))
            field_id = int(self._get_required_field(coordinates, 'fieldId'))
            time_point = int(self._get_required_field(coordinates, 'timepoint'))
            channel = int(coordinates.get('channel', 1))
            channel_name = coordinates.get('channelName', 'DAPI')
            coordinates_list.append(ImageCoords(x, y, time_point, z, field_id, channel, channel_name))
        pipeline.set_input(coordinates_list)

    def create_module(self, pipeline_id, module_data):
        pipeline = self._get_pipeline(pipeline_id)
        module_name = self._get_required_field(module_data, 'moduleName')
        module_id = self._get_required_field(module_data, 'moduleId')
        module_config = module_data.get('parameters')
        pipeline.add_module(module_name, module_id, module_config)

    def update_module(self, pipeline_id, module_id, module_data):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.edit_module(int(module_id), module_data)

    def move_module(self, pipeline_id, module_id, direction):
        pipeline = self._get_pipeline(pipeline_id)
        if not (direction == 'up' or direction == 'down'):
            raise RuntimeError("Direction value must be equal 'up' or 'down'")
        pipeline.move_module(module_id, direction)

    def delete_module(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.remove_module(module_id)

    def run_pipeline(self, pipeline_id):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.set_pipeline_state(PipelineState.CONFIGURING)
        delay = int(Config.RUN_DELAY)
        pool_size = int(Config.POOL_SIZE)
        try:
            while True:
                available_processors = pool_size - len(self.running_processes)
                if available_processors > 0:
                    if len(self.queue) == 0 or self.queue[0] == pipeline_id:
                        if len(self.queue) != 0:
                            self.queue.remove(pipeline_id)
                        pipeline.set_pipeline_state(PipelineState.RUNNING)
                        process = Process(target=pipeline.run_pipeline)
                        process.start()
                        print("[DEBUG] Run process '%s' started with PID %d" % (pipeline_id, process.pid))
                        self.running_processes.update({pipeline_id: process})
                        process.join()
                        print("[DEBUG] Run processes '%s' finished" % pipeline_id)
                        pipeline.set_pipeline_state(PipelineState.FINISHED)
                        self.running_processes.pop(pipeline_id)
                        return
                if not self.queue.__contains__(pipeline_id):
                    pipeline.set_pipeline_state(PipelineState.QUEUED)
                    self.queue.append(pipeline_id)
                    print("[DEBUG] Run '%s' queued" % pipeline_id)
                sleep(delay)
        except BaseException as e:
            error_description = str(e)
            pipeline.set_pipeline_state(PipelineState.FAILED, message=error_description)
            raise e

    def run_module(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        pipeline.run_module(module_id)

    def get_status(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        if not module_id:
            return pipeline.get_pipeline_status().to_json()
        return pipeline.get_module_status(module_id).to_json()

    def _get_pipeline(self, pipeline_id):
        pipeline = self.pipelines.get(pipeline_id)
        if not pipeline:
            raise RuntimeError("Failed to find pipeline '%s'" % pipeline_id)
        return pipeline

    @staticmethod
    def _get_required_field(json_data, field_name):
        field_value = json_data.get(field_name)
        if field_value is None:
            raise RuntimeError("Field '%s' is required" % field_name)
        return field_value
