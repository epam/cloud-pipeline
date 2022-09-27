import gc

import bioformats
from cellprofiler_core.image import ImageSetList
from cellprofiler_core.measurement import Measurements
from cellprofiler_core.object import ObjectSet
from cellprofiler_core.preferences import set_current_workspace_path, get_conserve_memory
from cellprofiler_core.workspace import Workspace
from cellprofiler_core.pipeline import Pipeline

GROUP_NUMBER = "Group_Number"
GROUP_INDEX = "Group_Index"
GROUP_LENGTH = "Group_Length"


class DebugMode:

    def __init__(self, pipeline: Pipeline):
        self.__pipeline = pipeline
        self.__debug_measurements = None
        self.__workspace = Workspace(self.__pipeline, None, None, None, None, None)
        self.__create_workspace()
        self.__debug_object_set = None
        self.__debug_image_set_list = None
        self.__keys = None
        self.__groupings = None
        self.__grouping_index = None
        self.__within_group_index = None
        self.__debug_outlines = None
        self.__debug_grids = None

    def start_debugging(self):  # TODO: fix if such pipeline was already launched (ask UI if such case possible)
        if not self.__workspace.refresh_image_set():
            raise RuntimeError("Failed to prepare run.")
        self.__debug_measurements = Measurements(copy=self.__workspace.measurements, mode="memory")
        self.__debug_object_set = ObjectSet(can_overwrite=True)
        self.__debug_image_set_list = ImageSetList(True)
        debug_workspace = Workspace(
            self.__pipeline,
            None,
            None,
            None,
            self.__debug_measurements,
            self.__debug_image_set_list,
            None
        )
        try:
            debug_workspace.set_file_list(self.__workspace.file_list)
            self.__keys, self.__groupings = self.__pipeline.get_groupings(debug_workspace)
            self.__grouping_index = 0
            self.__within_group_index = 0
            self.__pipeline.prepare_group(debug_workspace, self.__groupings[0][0], self.__groupings[0][1])
        finally:
            debug_workspace.set_file_list(None)
        self.__debug_outlines = {}
        if not self.__init_image_set():
            raise RuntimeError('Failed to start debug mode')
        return True

    def stop_debugging(self):
        if get_conserve_memory():
            gc.collect()
        bioformats.formatreader.clear_image_reader_cache()
        self.flush_results()

    def flush_results(self):
        """
         Call this method to perform aggregation tasks
        """
        workspace = Workspace(
            self.__pipeline, None, None, None, self.__debug_measurements, self.__debug_image_set_list, None
        )
        self.__pipeline.post_run(workspace)

    def prepare_module(self, module):
        """
        This method shall be used for freshly created modules only
        :param module: module for prepare
        :return:
        """
        debug_workspace = Workspace(
            self.__pipeline,
            None,
            None,
            None,
            self.__debug_measurements,
            self.__debug_image_set_list,
            None
        )
        module.prepare_group(debug_workspace, self.__groupings[0][0], self.__groupings[0][1])

    def run_module(self, module):
        self.__debug_measurements.add_image_measurement(GROUP_NUMBER, self.__grouping_index + 1)
        self.__debug_measurements.add_image_measurement(GROUP_INDEX, self.__within_group_index + 1)
        self.__debug_measurements.add_image_measurement(GROUP_LENGTH, len(self.__groupings[self.__grouping_index][1]))
        debug_workspace = Workspace(
            self.__pipeline,
            module,
            self.__debug_measurements,
            self.__debug_object_set,
            self.__debug_measurements,
            self.__debug_image_set_list,
            None,
            outlines=self.__debug_outlines,
        )
        self.__debug_grids = debug_workspace.set_grids(self.__debug_grids)
        self.__pipeline.run_module(module, debug_workspace)
        module.post_group(debug_workspace, self.__keys)
        debug_workspace.refresh()
        return True

    def is_last_image_set(self):
        return self.__debug_measurements.image_set_count == self.__debug_measurements.image_set_number

    def get_image_set_count(self):
        return self.__debug_measurements.image_set_count

    def next_image_set(self):
        keys, image_numbers = self.__groupings[self.__grouping_index]
        if len(image_numbers) == 0:
            return False
        self.__within_group_index = (self.__within_group_index + 1) % len(image_numbers)
        image_number = image_numbers[self.__within_group_index]
        self.__debug_measurements.next_image_set(image_number)
        self.__init_image_set()
        self.__debug_outlines = {}
        return True

    def __init_image_set(self):
        for module in self.__pipeline.modules():
            if module.is_input_module():
                if not self.run_module(module):
                    return False
        return True

    def __create_workspace(self):
        self.__workspace.create()
        self.__workspace.measurements.clear()
        self.__workspace.save_pipeline_to_measurements()
        set_current_workspace_path(None)
