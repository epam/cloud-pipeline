import inspect
import os
import sys

from cellprofiler.modules.classifyobjects import ClassifyObjects
from cellprofiler.modules.colortogray import ColorToGray
from cellprofiler.modules.combineobjects import CombineObjects
from cellprofiler.modules.convertimagetoobjects import ConvertImageToObjects
from cellprofiler.modules.correctilluminationapply import CorrectIlluminationApply
from cellprofiler.modules.correctilluminationcalculate import CorrectIlluminationCalculate
from cellprofiler.modules.crop import Crop
from cellprofiler.modules.editobjectsmanually import EditObjectsManually
from cellprofiler.modules.enhanceedges import EnhanceEdges
from cellprofiler.modules.enhanceorsuppressfeatures import EnhanceOrSuppressFeatures
from cellprofiler.modules.erodeimage import ErodeImage
from cellprofiler.modules.expandorshrinkobjects import ExpandOrShrinkObjects
from cellprofiler.modules.exporttospreadsheet import ExportToSpreadsheet
from cellprofiler.modules.flipandrotate import FlipAndRotate
from cellprofiler.modules.graytocolor import GrayToColor
from cellprofiler.modules.identifyobjectsingrid import IdentifyObjectsInGrid
from cellprofiler.modules.identifyobjectsmanually import IdentifyObjectsManually
from cellprofiler.modules.identifyprimaryobjects import IdentifyPrimaryObjects
from cellprofiler.modules.identifysecondaryobjects import IdentifySecondaryObjects
from cellprofiler.modules.identifytertiaryobjects import IdentifyTertiaryObjects
from cellprofiler.modules.invertforprinting import InvertForPrinting
from cellprofiler.modules.makeprojection import MakeProjection
from cellprofiler.modules.maskobjects import MaskObjects
from cellprofiler.modules.morph import Morph
from cellprofiler.modules.opening import Opening
from cellprofiler.modules.overlayobjects import OverlayObjects
from cellprofiler.modules.overlayoutlines import OverlayOutlines
from cellprofiler.modules.reducenoise import ReduceNoise
from cellprofiler.modules.relateobjects import RelateObjects
from cellprofiler.modules.saveimages import SaveImages
from cellprofiler.modules.smooth import Smooth
from cellprofiler.modules.splitormergeobjects import SplitOrMergeObjects
from cellprofiler.modules.tile import Tile
from cellprofiler.modules.trackobjects import TrackObjects
from cellprofiler.modules.unmixcolors import UnmixColors
from cellprofiler_core.module import Module
from cellprofiler_core.modules.align import Align
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
from cellprofiler_core.setting.choice import Choice
from cellprofiler_core.setting.subscriber import LabelSubscriber, ImageSubscriber
from cellprofiler_core.setting import Color, SettingsGroup, StructuringElement, Divider, Measurement, Binary
from cellprofiler_core.setting.text import Float, ImageName, Integer, Text


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
            setting.value = module_config[name]
        return setting

    def _str_to_bool(self, input_value):
        if not input_value:
            return None
        return input_value.lower() in ("true", "t")


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


class OutputModuleProcessor(ModuleProcessor):
    def __init__(self, save_root, module=None):
        ModuleProcessor.__init__(self, module)
        self.save_root = os.path.join(save_root, str(self.module.id))

    def _output_location(self):
        return 'Elsewhere...|' + self.save_root


class ExportToSpreadsheetModuleProcessor(OutputModuleProcessor):
    def new_module(self):
        return ExportToSpreadsheet()

    def generated_params(self):
        return {'Output file location': self._output_location()}


class SaveImagesModuleProcessor(OutputModuleProcessor):
    def new_module(self):
        return SaveImages()

    def generated_params(self):
        return {'Overwrite existing files without warning?': 'Yes',
                'Save with lossless compression?': 'No',
                'Append a suffix to the image file name?': 'Yes',
                'Output file location': self._output_location()}


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
        General strategy of converting module's configuration to dictionary
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


class IdentifyObjectsInGridModuleProcessor(ModuleProcessor):

    def new_module(self):
        return IdentifyObjectsInGrid()


class ClassifyObjectsModuleProcessor(OutputModuleProcessor):
    _CLASSIFICATIONS = 'classifications'
    _BIN_CHOICES = 'bin_spacing'
    _BIN_COUNT = 'bin_count'
    _BIN_NAMES = 'bin_names'
    _CAN_DELETE = 'can_delete'
    _CUSTOM_THRESHOLDS = 'custom_thresholds'
    _UPPER_THRESHOLD = 'upper_threshold'
    _LOWER_THRESHOLD = 'lower_threshold'
    _IMAGE_NAME = 'image_name'
    _MEASUREMENT = 'measurement'
    _OBJECT_NAME = 'object_name'
    _WANTS_BIN_NAMES = 'wants_bean_names'
    _WANTS_UPPER_BIN = 'wants_upper_bin'
    _WANTS_LOWER_BIN = 'wants_lower_bin'
    _WANTS_IMAGES = 'wants_images'

    def new_module(self):
        return ClassifyObjects()

    def get_list_key(self):
        return self._CLASSIFICATIONS

    def get_module_list_element(self):
        return self.module.single_measurements

    def build_settings_group_from_list_element(self, element_dict) -> SettingsGroup:
        component = SettingsGroup()
        component.bin_choice = Choice('Select bin spacing', ['Evenly spaced bins', 'Custom-defined bins'],
                                      value=element_dict[self._BIN_CHOICES])
        component.bin_count = Integer('Number of bins', value=element_dict[self._BIN_COUNT])
        component.bin_names = Text('Enter the bin names separated by commas', value=element_dict[self._BIN_NAMES])
        component.can_delete = self._str_to_bool(element_dict[self._CAN_DELETE])
        component.custom_thresholds = Text('Enter the custom thresholds separating the values between bins',
                                           value=element_dict[self._CUSTOM_THRESHOLDS])
        component.high_threshold = Float('Upper threshold', value=element_dict[self._UPPER_THRESHOLD])
        component.low_threshold = Float('Lower threshold', value=element_dict[self._LOWER_THRESHOLD])
        component.image_name = ImageName('Name the output image', value=element_dict[self._IMAGE_NAME])
        component.measurement = Measurement('Select the measurement to classify by', None,
                                            value=element_dict[self._MEASUREMENT])
        component.object_name = LabelSubscriber('Select the object to be classified',
                                                value=element_dict[self._OBJECT_NAME])
        component.wants_custom_names = Binary('Give each bin a name?',
                                              value=self._str_to_bool(element_dict[self._WANTS_BIN_NAMES]))
        component.wants_high_bin = Binary('Use a bin for objects above the threshold?',
                                          value=self._str_to_bool(element_dict[self._WANTS_UPPER_BIN]))
        component.wants_low_bin = Binary('Use a bin for objects below the threshold?',
                                         value=self._str_to_bool(element_dict[self._WANTS_LOWER_BIN]))
        component.wants_images = Binary('Retain an image of the classified objects?',
                                        value=self._str_to_bool(element_dict[self._WANTS_IMAGES]))
        return component

    def get_mandatory_args_length(self):
        return 22

    def build_list_elements(self, settings_dict):
        additional_images = list()
        for i in range(0, len(settings_dict), 13):
            additional_images.append({
                self._OBJECT_NAME: settings_dict[i].value,
                self._MEASUREMENT: settings_dict[i + 1].value,
                self._BIN_CHOICES: settings_dict[i + 2].value,
                self._BIN_COUNT: settings_dict[i + 3].value,
                self._LOWER_THRESHOLD: settings_dict[i + 4].value,
                self._WANTS_LOWER_BIN: settings_dict[i + 5].value,
                self._UPPER_THRESHOLD: settings_dict[i + 6].value,
                self._WANTS_UPPER_BIN: settings_dict[i + 7].value,
                self._CUSTOM_THRESHOLDS: settings_dict[i + 8].value,
                self._WANTS_BIN_NAMES: settings_dict[i + 9].value,
                self._BIN_NAMES: settings_dict[i + 10].value,
                self._WANTS_IMAGES: settings_dict[i + 11].value,
                self._IMAGE_NAME: settings_dict[i + 12].value
            })
        return additional_images

    def get_settings_as_dict(self):
        module_settings_dictionary = dict()
        module_settings = self.module.settings()
        general_setting = module_settings[:3]
        for setting in general_setting:
            module_settings_dictionary[setting.text] = self.map_setting_to_text_value(setting)
        objects_settings = module_settings[3:-19]
        module_settings_dictionary[self.get_list_key()] = self.build_list_elements(objects_settings)
        general_setting = module_settings[-19:]
        for setting in general_setting:
            module_settings_dictionary[setting.text] = self.map_setting_to_text_value(setting)
        return module_settings_dictionary

    def generated_params(self):
        return {'Select the location of the classifier model file': self._output_location()}


class ConvertImageToObjectsModuleProcessor(ModuleProcessor):

    def new_module(self):
        return ConvertImageToObjects()


class EditObjectsManuallyModuleProcessor(ModuleProcessor):

    def new_module(self):
        return EditObjectsManually()


class CombineObjectsModuleProcessor(ModuleProcessor):

    def new_module(self):
        return CombineObjects()


class IdentifyObjectsManuallyModuleProcessor(ModuleProcessor):

    def new_module(self):
        return IdentifyObjectsManually()


class SplitOrMergeObjectsModuleProcessor(ModuleProcessor):

    def new_module(self):
        return SplitOrMergeObjects()


class TrackObjectsModuleProcessor(ModuleProcessor):

    def new_module(self):
        return TrackObjects()
