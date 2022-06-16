import cellprofiler_core.preferences

cellprofiler_core.preferences.set_headless()
cellprofiler_core.preferences.set_awt_headless(True)

import cellprofiler_core.pipeline
import cellprofiler_core.utilities.java
import pathlib
import uuid
import os

from cellprofiler.modules.identifyprimaryobjects import IdentifyPrimaryObjects
from cellprofiler.modules.identifysecondaryobjects import IdentifySecondaryObjects
from cellprofiler.modules.overlayobjects import OverlayObjects
from cellprofiler.modules.overlayoutlines import OverlayOutlines
from cellprofiler.modules.relateobjects import RelateObjects
from cellprofiler.modules.saveimages import SaveImages
from cellprofiler_core.image import ImageSetList
from cellprofiler_core.measurement import Measurements
from cellprofiler_core.module import Module
from cellprofiler_core.modules.groups import Groups
from cellprofiler_core.modules.images import Images
from cellprofiler_core.modules.metadata import Metadata
from cellprofiler_core.modules.namesandtypes import NamesAndTypes
from cellprofiler.modules.closing import Closing
from cellprofiler.modules.convertobjectstoimage import ConvertObjectsToImage
from cellprofiler.modules.erodeobjects import ErodeObjects
from cellprofiler.modules.imagemath import ImageMath
from cellprofiler.modules.maskimage import MaskImage
from cellprofiler.modules.medianfilter import MedianFilter
from cellprofiler.modules.removeholes import RemoveHoles
from cellprofiler.modules.rescaleintensity import RescaleIntensity
from cellprofiler.modules.resize import Resize
from cellprofiler.modules.resizeobjects import ResizeObjects
from cellprofiler.modules.threshold import Threshold
from cellprofiler.modules.watershed import Watershed
from cellprofiler_core.setting import Color, SettingsGroup, StructuringElement
from cellprofiler_core.setting.subscriber import LabelSubscriber
from cellprofiler_core.workspace import Workspace
from enum import Enum
from typing import List


MANDATORY_MODULES_COUNT = 4
INPUT_DATASET_NAME = 'input'
RAW_IMAGE_DATA_ROOT = os.getenv('HCS_RAW_IMAGE_DATA_ROOT')


class PipelineState(Enum):
    CONFIGURING = 1
    RUNNING = 2
    FINISHED = 3
    FAILED = 4


class ImageCoords(object):

    def __init__(self,  well_x, well_y, timepoint, z_plane, field, channel=1):
        self.well_x = well_x
        self.well_y = well_y
        self.timepoint = timepoint
        self.z_plane = z_plane
        self.field = field
        self.channel = channel


class HcsPipeline(object):

    def __init__(self, service_root_dir, measurement_id):
        self._pipeline = cellprofiler_core.pipeline.Pipeline()
        self._pipeline_id = str(uuid.uuid4())
        self._pipeline_output_dir = self._init_results_dir(service_root_dir, measurement_id)
        self._pipeline_input_dir = os.path.join(RAW_IMAGE_DATA_ROOT, measurement_id)
        self._pipeline_output_dir_cloud_path = self._extract_cloud_path()
        self._add_default_modules()
        self._pipeline_state = PipelineState.CONFIGURING
        self._pipeline_state_message = ''
        cellprofiler_core.preferences.set_headless()

    def set_pipeline_state(self, status: PipelineState, message: str = ''):
        self._pipeline_state = status
        self._pipeline_state_message = message

    def get_id(self):
        return self._pipeline_id

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
            run = self._pipeline.run()
            self.set_pipeline_state(PipelineState.FINISHED)
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
        processor = self._find_processor_by_name(module, module_name)
        module = processor.configure_module(module_config)
        return module

    def _find_processor_by_name(self, module, module_name):
        module_name = module_name.strip()
        if module_name == 'Images':
            processor = ImagesModuleProcessor(module)
        elif module_name == 'Metadata':
            processor = MetadataModuleProcessor(module)
        elif module_name == 'NamesAndTypes':
            processor = NamesAndTypesModuleProcessor(module)
        elif module_name == 'Groups':
            processor = GroupsModuleProcessor(module)
        elif module_name == 'IdentifyPrimaryObjects':
            processor = IdentifyPrimaryObjectsModuleProcessor(module)
        elif module_name == 'IdentifySecondaryObjects':
            processor = IdentifySecondaryObjectsModuleProcessor(module)
        elif module_name == 'OverlayObjects':
            processor = OverlayObjectsModuleProcessor(module)
        elif module_name == 'RelateObjects':
            processor = RelateObjectsModuleProcessor(module)
        elif module_name == 'OverlayOutlines':
            processor = OverlayOutlinesModuleProcessor(module)
        elif module_name == 'SaveImages':
            processor = SaveImagesModuleProcessor(self._pipeline_output_dir, module)
        elif module_name == 'RescaleIntensity':
            processor = RescaleIntensityModuleProcessor(module)
        elif module_name == 'Resize':
            processor = ResizeModuleProcessor(module)
        elif module_name == 'MedianFilter':
            processor = MedianFilterModuleProcessor(module)
        elif module_name == 'Threshold':
            processor = ThresholdModuleProcessor(module)
        elif module_name == 'RemoveHoles':
            processor = RemoveHolesModuleProcessor(module)
        elif module_name == 'Watershed':
            processor = WatershedModuleProcessor(module)
        elif module_name == 'ResizeObjects':
            processor = ResizeObjectsModuleProcessor(module)
        elif module_name == 'ErodeObjects':
            processor = ErodeObjectsModuleProcessor(module)
        elif module_name == 'ConvertObjectsToImage':
            processor = ConvertObjectsToImageModuleProcessor(module)
        elif module_name == 'ImageMath':
            processor = ImageMathModuleProcessor(module)
        elif module_name == 'Closing':
            processor = ClosingModuleProcessor(module)
        elif module_name == 'MaskImage':
            processor = MaskImageModuleProcessor(module)
        else:
            raise RuntimeError('Unsupported module type {}'.format(module_name))
        return processor

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

    def set_input(self, image_coords_list: List[ImageCoords]):
        self.set_pipeline_files([self._map_to_file_name(image) for image in image_coords_list])

    def _map_to_file_name(self, coords: ImageCoords):
        well_full_name = "r{:02d}c{:02d}".format(coords.well_x, coords.well_y)
        image_relative_path = 'images/{well}/{well}f{field:02d}p{plane:02d}-ch{channel:02d}t{timepoint:02d}.tiff' \
            .format(well=well_full_name, field=coords.field, plane=coords.z_plane,
                    channel=coords.channel, timepoint=coords.timepoint)
        return os.path.join(self._pipeline_input_dir, image_relative_path)

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
        self.add_module('Metadata', 2, {})
        self.add_module('NamesAndTypes', 3, {'Name to assign these images': INPUT_DATASET_NAME})
        self.add_module('Groups', 4, {})

    def _init_results_dir(self, service_root_dir, measurement_id):
        dir_path = os.path.join(service_root_dir, measurement_id, self._pipeline_id)
        if not os.path.exists(dir_path):
            os.makedirs(dir_path, mode=0o777, exist_ok=True)
        return dir_path

    def _map_module_to_summary(self, module: Module):
        summary = dict()
        module_name = module.module_name
        summary['name'] = module_name
        summary['id'] = str(module.id)
        processor = self._find_processor_by_name(module, module_name)
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


class ModuleProcessor(object):

    def __init__(self, raw_module: Module = None):
        if raw_module is None:
            raw_module = self.new_module()
        self.module = raw_module

    def new_module(self):
        return None

    def generated_params(self):
        return {}

    def get_settings_as_dict(self):
        settings = dict()
        for setting in self.module.settings():
            settings[setting.text] = self.map_setting_to_text_value(setting)
        return settings

    def map_setting_to_text_value(self, setting):
        return setting.value

    def configure_module(self, module_config: dict) -> Module:
        default_settings = self.module.settings()
        module_config.update(self.generated_params())
        actual_settings = [self._process_setting(setting, module_config) for setting in default_settings]
        self.module.set_settings(actual_settings)
        return self.module

    def _process_setting(self, setting, module_config: dict):
        name = setting.text
        if name in module_config:
            setting.value = module_config[name]
        return setting


class StructuringElementImagesModuleProcessor(ModuleProcessor):

    def map_setting_to_text_value(self, setting):
        return setting.value_text if isinstance(setting, StructuringElement) else setting.value

    def configure_module(self, module_config: dict) -> Module:
        for setting in self.module.settings():
            if isinstance(setting, StructuringElement):
                setting_name = setting.text
                if setting_name in module_config:
                    structuring_setting_value = module_config.pop(setting_name)
                    setting.set_value(structuring_setting_value)
        ModuleProcessor.configure_module(self, module_config)
        return self.module


class ImagesModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Images()


class MetadataModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Metadata()


class NamesAndTypesModuleProcessor(ModuleProcessor):
    def new_module(self):
        return NamesAndTypes()


class GroupsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Groups()


class IdentifyPrimaryObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return IdentifyPrimaryObjects()


class OverlayObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return OverlayObjects()


class IdentifySecondaryObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return IdentifySecondaryObjects()


class RelateObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return RelateObjects()


class RescaleIntensityModuleProcessor(ModuleProcessor):
    def new_module(self):
        return RescaleIntensity()


class ResizeModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Resize()


class MedianFilterModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MedianFilter()


class ThresholdModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Threshold()


class RemoveHolesModuleProcessor(ModuleProcessor):
    def new_module(self):
        return RemoveHoles()


class WatershedModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return Watershed()


class ResizeObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ResizeObjects()


class ConvertObjectsToImageModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ConvertObjectsToImage()


class ErodeObjectsModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return ErodeObjects()


class ImageMathModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ImageMath()


class ClosingModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return Closing()


class MaskImageModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MaskImage()


class OverlayOutlinesModuleProcessor(ModuleProcessor):

    OUTPUT_OBJECTS_KEY = 'output'

    def new_module(self):
        return OverlayOutlines()

    def configure_module(self, module_config: dict) -> Module:
        if self.OUTPUT_OBJECTS_KEY in module_config:
            self.module.outlines.clear()
            objects = module_config.pop(self.OUTPUT_OBJECTS_KEY)
            for object_rule in objects:
                values = object_rule.split('|')
                group = SettingsGroup()
                group.append('objects_name', LabelSubscriber('Select objects to display', value=values[0]))
                group.append('color', Color('Select outline color', value=values[1]))
                self.module.outlines.append(group)
        ModuleProcessor.configure_module(self, module_config)
        return self.module


class SaveImagesModuleProcessor(ModuleProcessor):
    def __init__(self, save_root, module=None):
        ModuleProcessor.__init__(self, module)
        self.save_root = os.path.join(save_root, str(self.module.id))

    def new_module(self):
        return SaveImages()

    def generated_params(self):
        return {'Overwrite existing files without warning?': 'Yes',
                'Save with lossless compression?': 'No',
                'Append a suffix to the image file name?': 'Yes',
                'Output file location': 'Elsewhere...|{}'.format(self.save_root)}
