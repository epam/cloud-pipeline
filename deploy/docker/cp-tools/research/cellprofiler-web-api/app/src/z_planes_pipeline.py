from typing import List

from cellprofiler_core.setting import Binary, SettingsGroup
from cellprofiler_core.setting.choice import Choice

from .hcs_pipeline import HcsPipeline, ImageCoords


class ZPlanesPipeline(HcsPipeline):
    METADATA_CATEGORY_CHOICES = ['WellRow', 'WellColumn', 'Field', 'Timepoint']
    DEFAULT_BIT_DEPTH = '16-bit integer'

    def __init__(self, measurements_uuid, bit_depth=DEFAULT_BIT_DEPTH):
        HcsPipeline.__init__(self, measurements_uuid)
        self.parent_pipeline_inputs = list()
        self.pipeline_inputs = list()
        self.bit_depth = bit_depth

    def set_input(self, coordinates_list: List[ImageCoords]):
        z_planes_map = self._construct_z_plane_groups(coordinates_list)
        self._filter_pipeline_inputs(z_planes_map)

        if self.is_empty():
            return

        super().set_input(self.pipeline_inputs)
        self._construct_grouping_module()
        self._construct_extra_modules()

    def is_empty(self):
        return not self.pipeline_inputs or len(self.pipeline_inputs) == 0

    def _filter_pipeline_inputs(self, z_planes_map: dict):
        for group_key, planes in z_planes_map.items():
            if not planes or len(planes) == 0:
                continue
            # groups with single z-plane shall not be squashed and no processing required
            if len(planes) == 1:
                self.parent_pipeline_inputs.append(planes[0])
                continue
            for image_coordinates in planes:
                self.pipeline_inputs.append(image_coordinates)

    def _construct_grouping_module(self):
        self._pipeline.modules()[3].wants_groups = Binary('Do you want to group your images?', True)
        self._pipeline.modules()[3].grouping_metadata.clear()
        for metadata_category in self.METADATA_CATEGORY_CHOICES:
            self._pipeline.modules()[3].grouping_metadata.append(self._groups_setting_group(metadata_category))

    def _construct_extra_modules(self):
        channels_map = {image.channel_name: image.channel for image in self.pipeline_inputs}

        module_id = 4
        for channel_name in channels_map.keys():
            module_id += 1
            self.add_module('MakeProjection', module_id, self._make_projection_module_config(channel_name))
            module_id += 1
            self.add_module('SaveImages', module_id,
                            self._save_projection_images_module_config(channel_name, self.bit_depth))

    def _construct_z_plane_groups(self, coordinates_list: List[ImageCoords]):
        z_planes_map = dict()
        for coordinates in coordinates_list:
            current_z_plane = coordinates.z_plane
            if current_z_plane not in self._z_planes:
                self.parent_pipeline_inputs.append(coordinates)
                continue
            z_planes_group_key = (coordinates.well_x, coordinates.well_y, coordinates.timepoint,
                                  coordinates.field, coordinates.channel, coordinates.channel_name)
            if z_planes_map.get(z_planes_group_key) is None:
                z_planes_map.update({z_planes_group_key: list()})
            z_planes_map.get(z_planes_group_key).append(coordinates)
        return z_planes_map

    @staticmethod
    def _make_projection_module_config(channel_name):
        return {
            'Select the input image': channel_name,
            'Type of projection': 'Maximum',
            'Name the output image': '%s-projection' % channel_name
        }

    @staticmethod
    def _save_projection_images_module_config(channel_name, bit_depth=DEFAULT_BIT_DEPTH):
        return {
            'Select the type of image to save': 'Image',
            'Select the image to save': '%s-projection' % channel_name,
            'Select method for constructing file names': 'From image filename',
            'Image bit depth': bit_depth or ZPlanesPipeline.DEFAULT_BIT_DEPTH,
            'When to save': 'Last cycle',
            'Select image name for file prefix': channel_name,
            'Record the file and path information to the saved image?': 'No',
            'Create subfolders in the output folder?': 'No'
        }

    @staticmethod
    def _groups_setting_group(metadata_category):
        group = SettingsGroup()
        group.metadata_choice = Choice('Metadata category', [metadata_category], value=metadata_category)
        return group
