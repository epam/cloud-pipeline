import numpy
from cellprofiler_core.image import ImageSetList
from cellprofiler_core.measurement import Measurements
from cellprofiler_core.workspace import Workspace

from .image_set_group import ImageSetGroup

IMAGE = "Image"
GROUP_NUMBER = "Group_Number"
GROUP_INDEX = "Group_Index"


class DebugMode:

    def __init__(self, pipeline):
        self.pipeline = pipeline
        self.image_set_start = 1
        self.image_set_end = None
        self.workspace = None
        self.measurements = None
        self.groups = []
        self.last_group = None

    def prepare(self, modules):
        self.measurements = Measurements(
            image_set_start=self.image_set_start,
            filename=None,
            copy=None,
        )
        self.measurements.is_first_image = True
        self.workspace = Workspace(
            pipeline=self.pipeline,
            module=None,
            image_set=None,
            object_set=None,
            measurements=self.measurements,
            image_set_list=ImageSetList(),
            frame=None)
        if not self.pipeline.prepare_run(self.workspace):
            raise RuntimeError("Failed to prepare run")
        self.groups, self.last_group = self.prepare_groups(modules)

    def prepare_groups(self, modules):
        keys, groupings = self.pipeline.get_groupings(self.workspace)

        results = []
        for gn, (grouping_keys, image_numbers) in enumerate(groupings):
            need_to_run_prepare_group = True

            for gi, image_number in enumerate(image_numbers):
                if image_number < self.image_set_start:
                    continue
                if self.image_set_end is not None and image_number > self.image_set_end:
                    continue
                if self.measurements is not None and all(
                        [self.measurements.has_feature(IMAGE, f) for f in (GROUP_NUMBER, GROUP_INDEX,)]):
                    group_number, group_index = \
                        [self.measurements[IMAGE, f, image_number] for f in (GROUP_NUMBER, GROUP_INDEX,)]
                else:
                    group_number = gn + 1
                    group_index = gi + 1

                if need_to_run_prepare_group:
                    results.append(ImageSetGroup.first_image_set(self.workspace, modules, grouping_keys, image_numbers,
                                                                 group_number=group_number,
                                                                 group_index=group_index,
                                                                 image_number=image_number))
                else:
                    results.append(ImageSetGroup.intermediate_image_set(self.workspace,
                                                                        group_number=group_number,
                                                                        group_index=group_index,
                                                                        image_number=image_number))
                need_to_run_prepare_group = False
            if not need_to_run_prepare_group:
                results.append(ImageSetGroup.last_image_set(self.workspace, modules, grouping_keys))
        return results[:-1], results[-1:][0]

    def launch(self, modules_to_process):
        image_set_count = -1
        for group in self.groups:
            image_set_count += 1
            group.workspace = self.workspace
            self.measurements = group.set_measurements(self.measurements)

            for module in modules_to_process:
                self.workspace = self.run_module(module, group)

    def run_module(self, module, group):
        if module.should_stop_writing_measurements():
            group.should_write_measurements = False

        workspace = Workspace(
            pipeline=self.pipeline,
            module=module,
            image_set=group.image_set,
            object_set=group.object_set,
            measurements=self.measurements,
            image_set_list=ImageSetList(),
            frame=None,
            outlines=group.outlines,
        )
        group.grids = workspace.set_grids(group.grids)

        try:
            module.run(workspace)
        except Exception:
            print("Error detected during run of module %s", module.module_name)
        workspace.refresh()

        # Paradox: ExportToDatabase must write these columns in order
        #  to complete, but in order to do so, the module needs to
        #  have already completed. So we don't report them for it.
        if module.module_name != "Restart" and group.should_write_measurements:
            module_error_measurement = "ModuleError_%02d%s" % (module.module_num, module.module_name)
            execution_time_measurement = "ExecutionTime_%02d%s" % (module.module_num, module.module_name)
            self.measurements.add_measurement("Image", module_error_measurement, numpy.array([0]))
            self.measurements.add_measurement("Image", execution_time_measurement, numpy.array([float(0.1)]))
        return workspace

    def post_launch(self, modules):
        if self.last_group.image_number is None:
            if not self.last_group._post_group(workspace=self.workspace, modules=modules):
                self.measurements.add_experiment_measurement("Exit_Status", "Failure")
                raise RuntimeError("Error")
        for module in modules:
            module.post_run(self.workspace)
        self.measurements.flush()
        return self.workspace

    def close(self):
        self.measurements.flush()

        if self.measurements is not None:
            workspace = Workspace(pipeline=self.pipeline,
                                  module=None,
                                  image_set=None,
                                  object_set=None,
                                  measurements=self.measurements,
                                  image_set_list=ImageSetList(),
                                  frame=None)
            exit_status = self.pipeline.post_run(workspace)
            self.measurements.add_experiment_measurement("Exit_Status", exit_status)
        if self.measurements is not None:
            # XXX - We want to force the measurements to update the
            # underlying file, or else we get partially written HDF5
            # files.  There must be a better way to do this.
            self.measurements.flush()
            del self.measurements
        self.pipeline.end_run()
