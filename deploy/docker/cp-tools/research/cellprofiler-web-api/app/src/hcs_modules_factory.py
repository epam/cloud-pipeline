import inspect
import os
import sys

from cellprofiler.modules.colortogray import ColorToGray
from cellprofiler.modules.correctilluminationapply import CorrectIlluminationApply
from cellprofiler.modules.correctilluminationcalculate import CorrectIlluminationCalculate
from cellprofiler.modules.crop import Crop
from cellprofiler.modules.definegrid import DefineGrid
from cellprofiler.modules.dilateimage import DilateImage
from cellprofiler.modules.dilateobjects import DilateObjects
from cellprofiler.modules.enhanceedges import EnhanceEdges
from cellprofiler.modules.erodeimage import ErodeImage
from cellprofiler.modules.expandorshrinkobjects import ExpandOrShrinkObjects
from cellprofiler.modules.exporttospreadsheet import ExportToSpreadsheet, EEObjectNameSubscriber
from cellprofiler.modules.fillobjects import FillObjects
from cellprofiler.modules.flipandrotate import FlipAndRotate
from cellprofiler.modules.gaussianfilter import GaussianFilter
from cellprofiler.modules.graytocolor import GrayToColor
from cellprofiler.modules.identifyprimaryobjects import IdentifyPrimaryObjects
from cellprofiler.modules.identifysecondaryobjects import IdentifySecondaryObjects
from cellprofiler.modules.identifytertiaryobjects import IdentifyTertiaryObjects
from cellprofiler.modules.invertforprinting import InvertForPrinting
from cellprofiler.modules.makeprojection import MakeProjection
from cellprofiler.modules.maskobjects import MaskObjects
from cellprofiler.modules.matchtemplate import MatchTemplate
from cellprofiler.modules.measureimageareaoccupied import MeasureImageAreaOccupied
from cellprofiler.modules.measureimageintensity import MeasureImageIntensity
from cellprofiler.modules.measureobjectintensity import MeasureObjectIntensity
from cellprofiler.modules.measureobjectsizeshape import MeasureObjectSizeShape
from cellprofiler.modules.medialaxis import MedialAxis
from cellprofiler.modules.morph import Morph
from cellprofiler.modules.morphologicalskeleton import MorphologicalSkeleton
from cellprofiler.modules.opening import Opening
from cellprofiler.modules.overlayobjects import OverlayObjects
from cellprofiler.modules.overlayoutlines import OverlayOutlines
from cellprofiler.modules.reducenoise import ReduceNoise
from cellprofiler.modules.relateobjects import RelateObjects
from cellprofiler.modules.savecroppedobjects import SaveCroppedObjects
from cellprofiler.modules.saveimages import SaveImages
from cellprofiler.modules.shrinktoobjectcenters import ShrinkToObjectCenters
from cellprofiler.modules.smooth import Smooth
from cellprofiler.modules.tile import Tile
from cellprofiler.modules.unmixcolors import UnmixColors
from cellprofiler_core.module import Module
from cellprofiler_core.modules.align import Align
from cellprofiler_core.modules.groups import Groups
from cellprofiler_core.modules.images import Images
from cellprofiler_core.modules.metadata import Metadata
from cellprofiler_core.modules.namesandtypes import NamesAndTypes
from cellprofiler.modules.closing import Closing
from cellprofiler.modules.convertobjectstoimage import ConvertObjectsToImage
from cellprofiler.modules.enhanceorsuppressfeatures import EnhanceOrSuppressFeatures
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
from cellprofiler_core.setting.choice import Choice
from cellprofiler_core.setting.subscriber import LabelSubscriber, ImageSubscriber
from cellprofiler_core.setting import Color, SettingsGroup, StructuringElement, Divider, Measurement, Binary
from cellprofiler_core.setting.text import Float, ImageName, Text
from .modules.define_results import DefineResults


class HcsModulesFactory(object):

    def __init__(self, pipeline_output_dir):
        self._pipeline_output_dir = pipeline_output_dir
        self._output_modules_processors = dict()
        self._modules_processors = dict()
        for name, clazz in inspect.getmembers(sys.modules[__name__]):
            if inspect.isclass(clazz):
                base_classes = [base_class.__name__ for base_class in inspect.getmro(clazz)]
                if 'OutputModuleProcessor' in base_classes:
                    self._output_modules_processors[name[:-15]] = clazz
                elif 'ModuleProcessor' in base_classes:
                    self._modules_processors[name[:-15]] = clazz

    def get_module_processor(self, module: Module, module_name: str):
        if module is not None:
            module_name = module.module_name
        if module_name is None:
            raise RuntimeError('Either a module or target module name should be specified!')
        module_name = module_name.strip()
        if module_name in self._output_modules_processors:
            return self._output_modules_processors[module_name](self._pipeline_output_dir, module)
        elif module_name in self._modules_processors:
            return self._modules_processors[module_name](module)
        else:
            raise RuntimeError('Unsupported module type [{}]'.format(module_name))


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
            new_value = module_config[name]
            if isinstance(setting.value, list):
                setting.value.clear()
                setting.value.extend(new_value)
            else:
                setting.value = new_value
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


class IdentifyTertiaryObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return IdentifyTertiaryObjects()


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


class ErodeImageModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return ErodeImage()


class ErodeObjectsModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return ErodeObjects()


class ClosingModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return Closing()


class OpeningModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return Opening()


class DilateImageModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return DilateImage()


class DilateObjectsModuleProcessor(StructuringElementImagesModuleProcessor):
    def new_module(self):
        return DilateObjects()


class MaskImageModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MaskImage()


class EnhanceOrSuppressFeaturesModuleProcessor(ModuleProcessor):
    def new_module(self):
        return EnhanceOrSuppressFeatures()


class CorrectIlluminationApplyModuleProcessor(ModuleProcessor):
    def new_module(self):
        return CorrectIlluminationApply()


class SmoothModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Smooth()


class ReduceNoiseModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ReduceNoise()


class MaskObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MaskObjects()


class ExpandOrShrinkObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ExpandOrShrinkObjects()


class ColorToGrayModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ColorToGray()


class CorrectIlluminationCalculateModuleProcessor(ModuleProcessor):
    def new_module(self):
        return CorrectIlluminationCalculate()


class CropModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Crop()


class EnhanceEdgesModuleProcessor(ModuleProcessor):
    def new_module(self):
        return EnhanceEdges()


class FlipAndRotateModuleProcessor(ModuleProcessor):
    def new_module(self):
        return FlipAndRotate()


class GrayToColorModuleProcessor(ModuleProcessor):
    def new_module(self):
        return GrayToColor()


class InvertForPrintingModuleProcessor(ModuleProcessor):
    def new_module(self):
        return InvertForPrinting()


class MakeProjectionModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MakeProjection()


class MorphModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Morph()


class TileModuleProcessor(ModuleProcessor):
    def new_module(self):
        return Tile()


class UnmixColorsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return UnmixColors()


class FillObjectsModuleProcessor(ModuleProcessor):
    def new_module(self):
        return FillObjects()


class GaussianFilterModuleProcessor(ModuleProcessor):
    def new_module(self):
        return GaussianFilter()


class MedialAxisModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MedialAxis()


class MorphologicalSkeletonModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MorphologicalSkeleton()


class ShrinkToObjectCentersModuleProcessor(ModuleProcessor):
    def new_module(self):
        return ShrinkToObjectCenters()


class DefineGridModuleProcessor(ModuleProcessor):
    def new_module(self):
        return DefineGrid()


class MeasureObjectSizeShapeModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MeasureObjectSizeShape()


class MeasureObjectIntensityModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MeasureObjectIntensity()


class MeasureImageAreaOccupiedModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MeasureImageAreaOccupied()


class MeasureImageIntensityModuleProcessor(ModuleProcessor):
    def new_module(self):
        return MeasureImageIntensity()


class MatchTemplateModuleProcessor(ModuleProcessor):

    _INPUT_TEMPLATE_PATH_KEY = 'Template'

    def new_module(self):
        return MatchTemplate()

    def configure_module(self, module_config: dict):
        self._check_template_path(module_config)
        return ModuleProcessor.configure_module(self, module_config)

    def _check_template_path(self, module_config, cloud_scheme='s3'):
        if self._INPUT_TEMPLATE_PATH_KEY not in module_config:
            raise RuntimeError('Template path should be specified in a module config!')
        template_path = module_config[self._INPUT_TEMPLATE_PATH_KEY]
        template_path = template_path if template_path is None else template_path.strip()
        if not template_path:
            raise RuntimeError('Template path should be specified in a module config!')
        cloud_prefix = cloud_scheme + '://'
        if template_path.startswith(cloud_prefix):
            template_path = os.path.join('/cloud-data', template_path[len(cloud_prefix):])
        if not os.path.exists(template_path):
            raise RuntimeError('No such file [{}] available!'.format(template_path))
        module_config[self._INPUT_TEMPLATE_PATH_KEY] = template_path


class OutputModuleProcessor(ModuleProcessor):
    def __init__(self, save_root, module=None):
        ModuleProcessor.__init__(self, module)
        self.save_root = os.path.join(save_root, str(self.module.id))

    def _output_location(self):
        return 'Elsewhere...|' + self.save_root


class ExportToSpreadsheetModuleProcessor(OutputModuleProcessor):
    _EXPORT_DATA_KEY = 'Data to export'
    _ALL_MEASUREMENT_TYPES_SELECTOR_KEY = 'Export all measurement types?'
    _EXPORT_MEASUREMENT_KEY = 'Press button to select measurements'
    _ALL_MEASUREMENT_SELECTOR_KEY = 'Select the measurements to export'

    def new_module(self):
        return ExportToSpreadsheet()

    def configure_module(self, module_config: dict) -> Module:
        if self._EXPORT_DATA_KEY in module_config:
            module_config[self._ALL_MEASUREMENT_TYPES_SELECTOR_KEY] = False
            self.module.object_groups.clear()
            objects = module_config.pop(self._EXPORT_DATA_KEY).split('|')
            for object_name in objects:
                group = SettingsGroup()
                group.file_name = Text('File name', object_name + '.csv')
                group.name = EEObjectNameSubscriber('Data to export', object_name)
                group.previous_file = Binary('Combine these object measurements with those of the previous object?',
                                             False)
                group.wants_automatic_file_name = Binary('Use the object name for the file name?', False)
                self.module.object_groups.append(group)
        module_config[self._ALL_MEASUREMENT_SELECTOR_KEY] = self._EXPORT_MEASUREMENT_KEY in module_config
        ModuleProcessor.configure_module(self, module_config)
        return self.module

    def get_settings_as_dict(self):
        settings = dict()
        for setting in self.module.settings():
            settings[setting.text] = self.map_setting_to_text_value(setting)
        if self._EXPORT_DATA_KEY in settings and settings[self._EXPORT_DATA_KEY] != 'Do not use':
            output_object_names = [output_object.name.value for output_object in self.module.object_groups]
            settings[self._EXPORT_DATA_KEY] = '|'.join(output_object_names)
            settings.pop('File name')
        return settings

    def generated_params(self):
        return {'Output file location': self._output_location(),
                'Add a prefix to file names?': 'No',
                'Overwrite existing files without warning?': 'Yes'}


class DefineResultsModuleProcessor(ExportToSpreadsheetModuleProcessor):
    def new_module(self):
        return DefineResults()

    def configure_module(self, module_config: dict) -> Module:
        specs = module_config['specs'] if 'specs' in module_config else []
        grouping = module_config['grouping'] if 'grouping' in module_config else None
        self._validate_configuration(specs, grouping)
        self.module.set_calculation_spec(specs, grouping)
        self.set_required_data_to_module_config(module_config, specs)
        ExportToSpreadsheetModuleProcessor.configure_module(self, module_config)
        return self.module

    def set_required_data_to_module_config(self, module_config, specs):
        all_objects = set()
        for spec in specs:
            all_objects.add(spec.primary)
            all_objects.add(spec.secondary)
        if None in all_objects:
            all_objects.remove(None)
        module_config[self._EXPORT_DATA_KEY] = '|'.join(all_objects)

    def generated_params(self):
        return {'Output file location': self._output_location(),
                'Add a prefix to file names?': 'No',
                'Overwrite existing files without warning?': 'Yes',
                'Add image metadata columns to your object data file?': 'Yes'}

    def _validate_configuration(self, specs, grouping):
        # TODO check grouping is presented in metadata
        # TODO check if a corresponding RelateObject module exists for objects, specified as primary and secondary
        for spec in specs:
            unknown_stat_functions = set(spec.stat_functions) - DefineResults.SUPPORTED_STAT_FUNCTIONS
            if len(unknown_stat_functions) > 0:
                raise RuntimeError('Unknown {} stat function(s) passed in a configuration. Supported ones: {}'
                                   .format(list(unknown_stat_functions), list(DefineResults.SUPPORTED_STAT_FUNCTIONS)))


class SaveImagesModuleProcessor(OutputModuleProcessor):
    def new_module(self):
        return SaveImages()

    def generated_params(self):
        return {'Overwrite existing files without warning?': 'Yes',
                'Save with lossless compression?': 'No',
                'Append a suffix to the image file name?': 'Yes',
                'Output file location': self._output_location()}


class SaveCroppedObjectsModuleProcessor(OutputModuleProcessor):
    def new_module(self):
        return SaveCroppedObjects()

    def generated_params(self):
        return {'Directory': self._output_location()}


# TODO refactor to use SettingsWithListElementModuleProcessor
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


class SettingsWithListElementModuleProcessor(ModuleProcessor):

    def get_list_key(self):
        """
        Used to retrieve a key while extracting the settings from config and converting settings to dictionary.
        :return: a string, that represents the key for the list element in module configuration dictionary
        """
        return ''

    def build_settings_group_from_list_element(self, element_dict) -> SettingsGroup:
        """
        Maps a dictionary onto settings group according to a class structure
        :param element_dict: dictionary containing
        :return: configured SettingsGroup entity
        """
        return SettingsGroup()

    def get_module_list_element(self):
        """
        Retrieve a list, containing target elements, from module
        """
        return list()

    def configure_module(self, module_config: dict) -> Module:
        """
        General strategy of configuring modules with some list elements
        :param module_config: config as a dictionary
        :return: module after all configuration steps
        """
        list_key = self.get_list_key()
        if list_key in module_config:
            module_list_element = self.get_module_list_element()
            module_list_element.clear()
            rules = module_config.pop(list_key)
            for rule in rules:
                module_list_element.append(self.build_settings_group_from_list_element(rule))
        ModuleProcessor.configure_module(self, module_config)
        return self.module

    def build_list_elements(self, settings_dict):
        """
        Convert all elements of adjustable part of a module configuration to elements list.
        :param settings_dict: all various elements as a dict
        :return: elements converted to list
        """
        return list()

    def get_mandatory_args_length(self):
        """
        Define count of values, that are always presented in a module's settings
        :return: border value
        """
        return 1

    def get_settings_as_dict(self):
        """
        General strategy of converting module's confguration to dictionary
        :return: all settings in a form of a dictionary, that can be JSON serialized
        """
        module_settings_dictionary = dict()
        module_settings = self.module.settings()
        mandatory_args_length = self.get_mandatory_args_length()
        general_setting = module_settings[:mandatory_args_length]
        for setting in general_setting:
            module_settings_dictionary[setting.text] = self.map_setting_to_text_value(setting)
        objects_settings = module_settings[mandatory_args_length:]
        module_settings_dictionary[self.get_list_key()] = self.build_list_elements(objects_settings)
        return module_settings_dictionary


class ImageMathModuleProcessor(SettingsWithListElementModuleProcessor):
    _ELEMENTS_KEY = 'images'
    _ELEMENT_TYPE_KEY = 'type'
    _ELEMENT_VALUE_KEY = 'value'
    _ELEMENT_FACTOR_KEY = 'factor'

    def new_module(self):
        return ImageMath()

    def get_list_key(self):
        return self._ELEMENTS_KEY

    def get_module_list_element(self):
        return self.module.images

    def build_settings_group_from_list_element(self, element_dict) -> SettingsGroup:
        type = element_dict[self._ELEMENT_TYPE_KEY]
        value = element_dict[self._ELEMENT_VALUE_KEY]
        component = SettingsGroup()
        component.divider = Divider()
        component.factor = Float('Multiply the image by')
        component.factor.value = float(element_dict[self._ELEMENT_FACTOR_KEY])
        if type == 'Image':
            component.image_name = ImageSubscriber('Select the image', value=value)
            component.image_or_measurement = Choice('Image or measurement?', ['Image', 'Measurement'],
                                                    value='Image')
            component.measurement = Measurement('Measurement', '')
        elif type == 'Measurement':
            component.image_name = ImageSubscriber('Select the image', value=None)
            component.image_or_measurement = Choice('Image or measurement?', ['Image', 'Measurement'],
                                                    value='Measurement')
            component.measurement = Measurement('Measurement', None, value=value)
        else:
            raise RuntimeError('Unknown type [{}]: should be Image or Measurement')
        return component

    def get_mandatory_args_length(self):
        return 9

    def build_list_elements(self, settings_dict):
        elements = list()
        for i in range(0, len(settings_dict), 4):
            element_type = settings_dict[i].value
            if element_type == 'Image':
                element_value = settings_dict[i + 1].value
            elif element_type == 'Measurement':
                element_value = settings_dict[i + 3].value
            else:
                element_value = 'Unknown type'
            elements.append({
                self._ELEMENT_TYPE_KEY: element_type,
                self._ELEMENT_VALUE_KEY: element_value,
                self._ELEMENT_FACTOR_KEY: settings_dict[i + 2].value
            })
        return elements


class AlignModuleProcessor(SettingsWithListElementModuleProcessor):
    _ADDITIONAL_IMAGES = 'additional_images'
    _IMAGE_NAME = 'image'
    _OUTPUT_IMAGE = 'output_image'
    _ALIGNMENT = 'alignment'

    def new_module(self):
        return Align()

    def get_list_key(self):
        return self._ADDITIONAL_IMAGES

    def get_module_list_element(self):
        return self.module.additional_images

    def build_settings_group_from_list_element(self, element_dict) -> SettingsGroup:
        component = SettingsGroup()
        component.input_image_name = ImageSubscriber('Select the additional image',
                                                     value=element_dict[self._IMAGE_NAME])
        component.output_image_name = ImageName('Name the output image', value=element_dict[self._OUTPUT_IMAGE])
        component.align_choice = Choice('Select how the alignment is to be applied',
                                        ['Similarly', 'Separately'], value=element_dict[self._ALIGNMENT])
        return component

    def get_mandatory_args_length(self):
        return 6

    def build_list_elements(self, settings_dict):
        additional_images = list()
        for i in range(0, len(settings_dict), 3):
            additional_images.append({
                self._IMAGE_NAME: settings_dict[i].value,
                self._OUTPUT_IMAGE: settings_dict[i + 1].value,
                self._ALIGNMENT: settings_dict[i + 2].value
            })
        return additional_images
