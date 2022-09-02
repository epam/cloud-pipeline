import cellprofiler_core.preferences

from .config import Config
from .modules.define_results import DefineResults

cellprofiler_core.preferences.set_headless()
cellprofiler_core.preferences.set_awt_headless(True)

import cellprofiler_core.pipeline
import cellprofiler_core.utilities.java
import pathlib
import uuid
import os

from .hcs_modules_factory import HcsModulesFactory
from cellprofiler_core.image import ImageSetList
from cellprofiler_core.measurement import Measurements
from cellprofiler_core.module import Module
from cellprofiler_core.setting.choice import Choice
from cellprofiler_core.setting.filter import Filter
from cellprofiler_core.setting.text import FileImageName, LabelName, Float
from cellprofiler_core.setting import SettingsGroup
from cellprofiler_core.workspace import Workspace
from enum import Enum
from typing import List


MANDATORY_MODULES_COUNT = 4
HCS_PIPELINE_METADATA_EXPRESSION = '^r(?P<WellRow>\\d{2})c(?P<WellColumn>\\d{2})f(?P<Field>\\d{2})p(?P<Plane>\\d{2})-ch(?P<ChannelNumber>\\d{2})t(?P<Timepoint>\\d{2})'


class PipelineState(Enum):
    CONFIGURING = 1
    RUNNING = 2
    FINISHED = 3
    FAILED = 4
    QUEUED = 5


class ImageCoords(object):

    def __init__(self,  well_x, well_y, timepoint, z_plane, field, channel=1, channel_name='DAPI'):
        self.well_x = well_x
        self.well_y = well_y
        self.timepoint = timepoint
        self.z_plane = z_plane
        self.field = field
        self.channel = channel
        self.channel_name = channel_name


class HcsPipeline(object):

    def __init__(self, measurement_id):
        self._pipeline = cellprofiler_core.pipeline.Pipeline()
        self._pipeline_id = str(uuid.uuid4())
        self._pipeline_output_dir = self._init_results_dir(measurement_id)
        self._pipeline_input_dir = os.path.join(Config.RAW_IMAGE_DATA_ROOT, measurement_id)
        self._pipeline_output_dir_cloud_path = self._extract_cloud_path()
        self._modules_factory = HcsModulesFactory(self._pipeline_output_dir)
        self._add_default_modules()
        self._pipeline_state = PipelineState.CONFIGURING
        self._pipeline_state_message = ''
        self._input_sets = set()
        self._fields_by_well = dict()
        self._measurement_uuid = measurement_id
        self._pre_processing_pipeline_id = None
        self._z_planes = None  # z-planes to squash
        self._channels_map = dict()
        cellprofiler_core.preferences.set_headless()

    def set_pipeline_state(self, status: PipelineState, message: str = ''):
        self._pipeline_state = status
        self._pipeline_state_message = message

    def set_pre_processing_pipeline(self, pipeline_id):
        self._pre_processing_pipeline_id = pipeline_id

    def get_pre_processing_pipeline(self):
        return self._pre_processing_pipeline_id

    def get_id(self):
        return self._pipeline_id

    def get_measurement(self):
        return self._measurement_uuid

    def set_z_planes(self, z_planes):
        self._z_planes = z_planes

    def get_module_outputs(self, module):
        module_id = str(module.id)
        module_dir = os.path.join(self._pipeline_output_dir, module_id)
        if os.path.exists(module_dir):
            pipeline_cloud_output_dir = self._pipeline_output_dir_cloud_path \
                if self._pipeline_output_dir_cloud_path is not None \
                else self._pipeline_output_dir
            return [os.path.join(pipeline_cloud_output_dir, module_id, file_name)
                    for file_name
                    in os.listdir(module_dir)]
        return []

    def get_structure(self):
        data = dict()
        data['id'] = str(self._pipeline_id)
        data_files = [os.path.basename(file) for file in self._pipeline.file_list]
        data['files'] = data_files
        all_modules = self._pipeline.modules()
        computational_modules = all_modules[MANDATORY_MODULES_COUNT:]
        data['modules'] = [self._map_module_to_summary(module) for module in computational_modules]
        data['state'] = self._pipeline_state.name
        data['message'] = self._pipeline_state_message
        data['inputs'] = list(self._input_sets)
        if self.get_pre_processing_pipeline():
            data['pre_process_pipeline'] = self.get_pre_processing_pipeline()
        return data

    def get_module_status(self):
        pass

    def edit_module(self, module_num: int, new_config: dict):
        module_num = self._adjust_module_number(module_num)
        self._verify_module_num(module_num)
        module = self._pipeline.modules()[module_num]
        self.configure_module(new_config, module=module)
        self.set_pipeline_state(PipelineState.CONFIGURING)

    def run_pipeline(self):
        cellprofiler_core.utilities.java.start_java()
        self.set_pipeline_state(PipelineState.RUNNING)
        try:
            print("[DEBUG] Run execution '%s' started" % self._pipeline_id)
            run = self._pipeline.run()
            self.set_pipeline_state(PipelineState.FINISHED)
            print("[DEBUG] Run execution '%s' finished" % self._pipeline_id)
            return run
        except Exception as e:
            error_description = str(e)
            self.set_pipeline_state(PipelineState.FAILED, message=error_description)
            raise RuntimeError('An error occurred during pipeline execution: {}'.format(error_description))

    def run_module(self, module_num: int):
        module_num = self._adjust_module_number(module_num)
        modules = self._verify_module_num(module_num)
        image_set_list = ImageSetList()
        measurements = Measurements(1)
        workspace = Workspace(self._pipeline, None, None, None, measurements, image_set_list, None)
        modules[module_num].run(workspace)

    def move_module(self, module_num: int, direction: str):
        module_num = self._verify_module_num(module_num)
        self._pipeline.move_module(module_num, direction)

    def add_module(self, module_name: str, module_pos: int, module_config: dict):
        module_pos = self._adjust_module_number(module_pos)
        module = self.configure_module(module_config, module_name=module_name)
        module.set_module_num(module_pos)
        self._pipeline.add_module(module)
        self.set_pipeline_state(PipelineState.CONFIGURING)

    def configure_module(self, module_config, module=None, module_name=None):
        if module is not None:
            module_name = module.module_name
        if module_name == DefineResults.MODULE_NAME:
            module_config.update({DefineResults.FIELDS_BY_WELL: self._fields_by_well})
            module_config.update({DefineResults.Z_PLANES: self._z_planes})
        processor = self._modules_factory.get_module_processor(module, module_name)
        module = processor.configure_module(module_config)
        return module

    def remove_module(self, module_num: int):
        module_num = self._adjust_module_number(module_num)
        self._verify_module_num(module_num)
        self._pipeline.remove_module(module_num)
        self.set_pipeline_state(PipelineState.CONFIGURING)

    def set_pipeline_files(self, file_paths: List[str]):
        files = [pathlib.Path(file).as_uri() for file in file_paths]
        self._pipeline.file_list.clear()
        self._pipeline.file_list.extend(files)
        self.set_pipeline_state(PipelineState.CONFIGURING)

    def set_input_sets(self):
        self._input_sets.clear()
        self._pipeline.modules()[2].assignments.clear()
        for channel_name, channel_number in self._channels_map.items():
            group = SettingsGroup()
            channel_predicate = 'and (file does contain "ch{:02d}")'.format(channel_number)
            group.rule_filter = Filter('Select the rule criteria', [channel_predicate], value=channel_predicate)
            group.image_name = FileImageName('Name to assign these images', value=channel_name)
            group.load_as_choice = Choice('Select the image type', ['Grayscale image'], value='Grayscale image')
            group.rescale = Choice('Set intensity range from', ['Image metadata'], value='Image metadata')
            group.manual_rescale = Float('Maximum intensity')
            group.manual_rescale.value = 255.0
            group.object_name = LabelName('Name to assign these objects', channel_name)
            self._pipeline.modules()[2].assignments.append(group)
            self._input_sets.add(channel_name)

    def set_input(self, image_coords_list: List[ImageCoords]):
        self.set_pipeline_state(PipelineState.CONFIGURING)
        self.set_pipeline_files([self.map_to_file_name(image) for image in image_coords_list])
        self._set_fields_by_well(image_coords_list)
        self._channels_map = {image.channel_name: image.channel for image in image_coords_list}
        self.set_input_sets()

    def map_to_file_name(self, coords: ImageCoords):
        well_full_name = "r{:02d}c{:02d}".format(coords.well_x, coords.well_y)
        image_relative_path = 'images/{well}/{well}f{field:02d}p{plane:02d}-ch{channel:02d}t{timepoint:02d}.tiff' \
            .format(well=well_full_name, field=coords.field, plane=coords.z_plane,
                    channel=coords.channel, timepoint=coords.timepoint)
        image_full_path = os.path.join(self._pipeline_input_dir, image_relative_path)
        if not os.path.exists(image_full_path):
            raise FileNotFoundError("The file '%s' does not exist." % image_full_path)
        return image_full_path

    def _verify_module_num(self, module_num: int):
        modules = self._pipeline.modules()
        if int(module_num) < MANDATORY_MODULES_COUNT:
            raise RuntimeError('Mandatory data modules can''t be removed!')
        module_length = len(modules)
        if int(module_num) > module_length:
            raise RuntimeError("Invalid module number, current pipeline length is {}".format(module_length))
        return modules

    def _add_default_modules(self):
        self.add_module('Images', 1, {})
        self.add_module('Metadata', 2,
                        {'Extract metadata?': 'Yes',
                         'Metadata extraction method': 'Extract from file/folder names',
                         'Metadata source': 'File name',
                         'Regular expression to extract from file name': HCS_PIPELINE_METADATA_EXPRESSION})
        self.add_module('NamesAndTypes', 3, {'Assign a name to': 'Images matching rules'})
        self.add_module('Groups', 4, {})

    def _init_results_dir(self, measurement_id):
        service_root_dir = Config.COMMON_RESULTS_DIR
        dir_path = os.path.join(service_root_dir, measurement_id, self._pipeline_id)
        if not os.path.exists(dir_path):
            os.makedirs(dir_path, mode=0o777, exist_ok=True)
        return dir_path

    def _map_module_to_summary(self, module: Module):
        summary = dict()
        module_name = module.module_name
        summary['name'] = module_name
        summary['id'] = str(module.id)
        processor = self._modules_factory.get_module_processor(module, module_name)
        summary['settings'] = processor.get_settings_as_dict()
        summary['outputs'] = self.get_module_outputs(module)
        return summary

    def _extract_cloud_path(self, cloud_scheme='s3'):
        path_chunks = self._pipeline_output_dir.split('/cloud-data/', 1)
        if len(path_chunks) != 2:
            return None
        return '{}://{}'.format(cloud_scheme, path_chunks[1])

    def _adjust_module_number(self, module_pos):
        module_pos = int(module_pos)
        if module_pos < 1:
            raise RuntimeError('Module position should be a positive number')
        module_pos = module_pos + MANDATORY_MODULES_COUNT
        return module_pos

    def _set_fields_by_well(self, image_coords_list: List[ImageCoords]):
        results = {}
        for image_coords in image_coords_list:
            well_key = (image_coords.well_y, image_coords.well_x)
            if results.get(well_key) is None:
                results.update({well_key: set()})
            results.get(well_key).add(image_coords.field)
        self._fields_by_well = results
