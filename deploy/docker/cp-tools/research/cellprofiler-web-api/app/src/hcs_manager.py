from .config import Config
from .hcs_pipeline import ImageCoords, HcsPipeline, PipelineState
from multiprocessing import Process
from time import sleep
import glob
import os
from .z_planes_pipeline import ZPlanesPipeline


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

    def add_files(self, pipeline_id: int, input_data: dict):
        pipeline = self._get_pipeline(pipeline_id)
        files_data = self._get_required_field(input_data, 'files')
        z_planes = input_data.get('zPlanes', None)
        bit_depth = input_data.get('bitDepth', None)
        coordinates_list = [self._parse_inputs(coordinates) for coordinates in files_data]
        pipeline.set_input(coordinates_list)
        if z_planes:
            self.squash_z_planes(pipeline_id, z_planes, files_data, bit_depth)

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

    def squash_z_planes(self, parent_pipeline_id: int, z_planes: list, files_data: list, bit_depth: str = None):
        parent_pipeline = self._get_pipeline(parent_pipeline_id)

        projection_pipeline = ZPlanesPipeline(parent_pipeline.get_measurement(), bit_depth)
        pipeline_id = projection_pipeline.get_id()
        self.pipelines.update({pipeline_id: projection_pipeline})
        parent_pipeline.set_pre_processing_pipeline(pipeline_id)

        projection_pipeline.set_z_planes(z_planes)
        coordinates = [self._parse_inputs(coordinates) for coordinates in files_data]
        projection_pipeline.set_input(coordinates)

        if projection_pipeline.is_empty():
            parent_pipeline.set_pre_processing_pipeline(None)
            parent_pipeline.set_z_planes(None)
            return
        parent_pipeline.set_z_planes(z_planes)

    def launch_pipeline(self, pipeline_id: int):
        parent_pipeline = self._get_pipeline(pipeline_id)
        pre_processing_pipeline = parent_pipeline.get_pre_processing_pipeline()
        if pre_processing_pipeline is not None:
            self._run_projection_pipeline(pre_processing_pipeline, parent_pipeline)
        self._run_pipeline(pipeline_id)

    def _run_pipeline(self, pipeline_id, parent_pipeline=None):
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
                        self._set_pipeline_state(pipeline, PipelineState.RUNNING, parent_pipeline)
                        process = Process(target=pipeline.run_pipeline)
                        process.start()
                        print("[DEBUG] Run process '%s' started with PID %d" % (pipeline_id, process.pid))
                        self.running_processes.update({pipeline_id: process})
                        process.join()
                        print("[DEBUG] Run process '%s' finished" % pipeline_id)
                        pipeline.set_pipeline_state(PipelineState.FINISHED)
                        self.running_processes.pop(pipeline_id)
                        return
                if not self.queue.__contains__(pipeline_id):
                    self._set_pipeline_state(pipeline, PipelineState.QUEUED, parent_pipeline)
                    self.queue.append(pipeline_id)
                    print("[DEBUG] Run '%s' queued" % pipeline_id)
                sleep(delay)
        except BaseException as e:
            self._set_pipeline_state(pipeline, PipelineState.FAILED, parent_pipeline, message=str(e))
            raise e

    def _run_projection_pipeline(self, projection_pipeline_id: int, parent_pipeline: HcsPipeline):
        projection_pipeline = self._get_pipeline(projection_pipeline_id)
        if projection_pipeline.is_empty():
            print("[DEBUG] No need to run projection pipeline.")
            return
        self._run_pipeline(projection_pipeline_id, parent_pipeline)
        parent_pipeline.set_pipeline_files(self._collect_parent_pipeline_inputs(parent_pipeline, projection_pipeline))
        parent_pipeline.set_input_sets()

    def _parse_inputs(self, coordinates):
        x = int(self._get_required_field(coordinates, 'x'))
        y = int(self._get_required_field(coordinates, 'y'))
        z = int(self._get_required_field(coordinates, 'z'))
        field_id = int(self._get_required_field(coordinates, 'fieldId'))
        time_point = int(self._get_required_field(coordinates, 'timepoint'))
        channel = int(coordinates.get('channel', 1))
        channel_name = coordinates.get('channelName', 'DAPI')
        return ImageCoords(x, y, time_point, z, field_id, channel, channel_name)

    def _collect_parent_pipeline_inputs(self, parent_pipeline: HcsPipeline, projection_pipeline: ZPlanesPipeline):
        parent_pipeline_inputs = self._collect_pipeline_tiff_outputs(projection_pipeline.get_id(),
                                                                     parent_pipeline.get_measurement())
        for image in projection_pipeline.parent_pipeline_inputs:
            parent_pipeline_inputs.append(parent_pipeline.map_to_file_name(image))
        return parent_pipeline_inputs

    @staticmethod
    def _get_required_field(json_data, field_name):
        field_value = json_data.get(field_name)
        if field_value is None:
            raise RuntimeError("Field '%s' is required" % field_name)
        return field_value

    @staticmethod
    def get_required_field(json_data, field_name):
        return HCSManager._get_required_field(json_data, field_name)

    @staticmethod
    def _collect_pipeline_tiff_outputs(pipeline_id, measurements_uuid):
        results = list()
        results_dir = os.path.join(Config.COMMON_RESULTS_DIR, measurements_uuid, pipeline_id)
        for tiff_file_path in glob.iglob(results_dir + '/**/*.tiff', recursive=True):
            results.append(tiff_file_path)
        return results

    @staticmethod
    def _set_pipeline_state(current_pipeline, pipeline_state, parent_pipeline=None, message=''):
        current_pipeline.set_pipeline_state(pipeline_state, message=message)
        if parent_pipeline:
            parent_pipeline.set_pipeline_state(pipeline_state, message=message)
