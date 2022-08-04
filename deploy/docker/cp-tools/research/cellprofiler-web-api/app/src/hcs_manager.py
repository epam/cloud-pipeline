from .hcs_pipeline import ImageCoords
from .hcs_pipeline_status_wrapper import HcsPipelineStatusWrapper


class HCSManager:

    def __init__(self, pipelines, pool):
        self.pipelines = pipelines
        self.pool = pool

    def create_pipeline(self, measurement_uuid):
        pipeline = HcsPipelineStatusWrapper(measurement_uuid)
        pipeline_id = pipeline.get_id()
        self.pipelines.update({pipeline_id: pipeline})
        return pipeline_id

    def get_pipeline(self, pipeline_id):
        pipeline = self._get_pipeline(pipeline_id)
        return pipeline.get_pipeline()

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
        self.pool.apply(pipeline.run_pipeline)

    def run_module(self, pipeline_id, module_id):
        pipeline = self._get_pipeline(pipeline_id)
        self.pool.apply_async(pipeline.run_module, [module_id])

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
