from cellprofiler_core.object import ObjectSet
from cellprofiler_core.module import Module


class ImageSetGroup:

    def __init__(self, workspace, group_number, group_index, image_number,
                 modules, grouping, image_numbers):
        self.group_number = group_number
        self.workspace = workspace
        self.group_index = group_index
        self.image_number = image_number
        self.modules = modules
        self.grouping = grouping
        self.image_numbers = image_numbers
        self.object_set = ObjectSet()
        self.outlines = {}
        self.should_write_measurements = True
        self.grids = None
        self.image_set = None
        self.is_first_image_set = False
        self.is_last_image_set = False

    def closure(self):
        if self.is_first_image_set:
            return self._prepare_group()
        return True

    def _prepare_group(self):
        """Prepare to start processing a new group

        workspace - the workspace containing the measurements and image set list
        grouping - a dictionary giving the keys and values for the group

        returns true if the group should be run
        """
        for module in self.modules:
            module.prepare_group(self.workspace, self.grouping, self.image_numbers)
        return True

    def _post_group(self, workspace, modules):
        """Do post-processing after a group completes

        workspace - the last workspace run
        """
        for module in modules:
            module.post_group(workspace, self.grouping)
            if module.show_window and module.__class__.display_post_group != Module.display_post_group:
                workspace.post_group_display(module)
        return True

    def set_measurements(self, measurements):
        if not self.closure():
            raise RuntimeError("Error")

        measurements.clear_cache()
        for provider in measurements.providers:
            provider.release_memory()

        measurements.next_image_set(self.image_number)
        if self.is_first_image_set:
            measurements.image_set_start = self.image_number
            measurements.is_first_image = True

        measurements.group_number = self.group_number
        measurements.group_index = self.group_index

        self.image_set = measurements

        return measurements

    @staticmethod
    def first_image_set(workspace, modules, grouping_keys, image_numbers,
                        group_number, group_index, image_number):
        data = ImageSetGroup(workspace, group_number, group_index, image_number,
                             modules, grouping_keys, image_numbers)
        data.is_first_image_set = True
        return data

    @staticmethod
    def intermediate_image_set(workspace, group_number, group_index, image_number):
        return ImageSetGroup(workspace, group_number, group_index, image_number, None, None, None)

    @staticmethod
    def last_image_set(workspace, modules, grouping_keys):
        data = ImageSetGroup(workspace, None, None, None, modules, grouping_keys, None)
        data.is_last_image_set = True
        return data
