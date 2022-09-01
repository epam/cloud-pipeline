import os
import pandas
from cellprofiler.modules.exporttospreadsheet import ExportToSpreadsheet
from pandas import Index, MultiIndex


class CalculationSpec(object):
    _SINGLE_OBJECT_OPERATIONS = ['Number of Objects', 'Mean Intensity', 'Background Intensity',
                                 'Corrected Intensity', 'Relative Object Intensity', 'Uncorrected Peak Intensity',
                                 'Contrast', 'Area', 'Region Intensity']

    def __init__(self, primary, operation, secondary=None, stat_functions=None, column_operation_name=None):
        self.primary = primary
        self.operation = operation
        self.stat_functions = stat_functions if stat_functions is not None else []
        self.secondary = secondary
        self.column_operation_name = column_operation_name
        self.formula = False

    @staticmethod
    def from_json(json: dict):
        return CalculationSpec(json.get('primary'), json.get('operation'), secondary=json.get('secondary'),
                               stat_functions=json.get('stat_functions'),
                               column_operation_name=json.get('column_operation_name'))

    def to_json(self):
        json = {'primary': self.primary, 'operation': self.operation, 'formula': False}
        if len(self.stat_functions) > 0:
            json['stat_functions'] = self.stat_functions
        if self.secondary is not None:
            json['secondary'] = self.secondary
        if self.column_operation_name is not None:
            json['column_operation_name'] = self.column_operation_name
        return json

    def requires_secondary(self):
        return self.operation not in self._SINGLE_OBJECT_OPERATIONS


class FormulaSpec(object):

    def __init__(self, expression, properties, column_operation_name):
        self.expression = expression if expression is not None else []
        properties_array = properties if properties is not None else []
        self.properties = [CalculationSpec.from_json(x) for x in properties_array]
        self.column_operation_name = column_operation_name
        self.formula = True

    @staticmethod
    def from_json(json: dict):
        return FormulaSpec(json.get('expression'), json.get('properties'), json.get('column_operation_name'))

    def to_json(self):
        json = {'formula': True}
        if len(self.expression) > 0:
            json['expression'] = self.expression
        if self.properties is not None:
            json['properties'] = [x.to_json() for x in self.properties]
        if self.column_operation_name is not None:
            json['column_operation_name'] = self.column_operation_name
        return json


class SpecItem(object):

    @staticmethod
    def from_json(json: dict):
        if json is None:
            return None
        if json.get('formula') is True:
            return FormulaSpec.from_json(json)
        return CalculationSpec.from_json(json)


class DefineResults(ExportToSpreadsheet):
    MODULE_NAME = 'DefineResults'
    FIELDS_BY_WELL = 'fields_by_well'
    Z_PLANES = 'z_planes'
    _METADATA_COLUMN_PREFIX = 'Metadata_'
    _MEAN_STAT_FUNC_KEY = 'Mean'
    _MEDIAN_STAT_FUNC_KEY = 'Median'
    _STANDARD_DEV_STAT_FUNC_KEY = 'StdDev'
    _COEFFICIENT_OF_VARIATION_STAT_FUNC_KEY = 'CV %'
    _SUM_STAT_FUNC_KEY = 'Sum'
    _MAX_STAT_FUNC_KEY = 'Max'
    _MIN_STAT_FUNC_KEY = 'Min'
    SUPPORTED_STAT_FUNCTIONS = {_MEAN_STAT_FUNC_KEY, _MEDIAN_STAT_FUNC_KEY, _STANDARD_DEV_STAT_FUNC_KEY,
                                _COEFFICIENT_OF_VARIATION_STAT_FUNC_KEY, _SUM_STAT_FUNC_KEY, _MAX_STAT_FUNC_KEY,
                                _MIN_STAT_FUNC_KEY}
    _AREA_SHAPE_AREA = 'AreaShape_Area'
    _MAX_INTENSITY = 'Intensity_MaxIntensity'
    _MEAN_INTENSITY = 'Intensity_MeanIntensity'
    _MEAN_EDGE_INTENSITY = 'Intensity_MeanIntensityEdge'
    _INTEGRATED_INTENSITY = 'Intensity_IntegratedIntensity'
    _WELL_ROW = "WellRow"
    _WELL_COLUMN = "WellColumn"
    _WELL = "Well"
    _PLANE = "Plane"

    def __init__(self):
        super(DefineResults, self).__init__()
        self._calculation_specs = []
        self._grouping = []
        self.module_name = self.MODULE_NAME
        self._fields_by_well = {}
        self._squashed_z_planes_column_value = None
        self._z_planes = list()

    def update_settings(self, setting: list):
        pass

    def set_calculation_spec(self, specs, grouping, fields_by_well, z_planes=None):
        self._calculation_specs = specs if specs is not None else []
        self._grouping = grouping
        if self._grouping and self._WELL not in self._grouping:
            self._grouping.append(self._WELL)
        if self._grouping and self._WELL_ROW not in self._grouping:
            self._grouping.append(self._WELL_ROW)
        if self._grouping and self._WELL_COLUMN not in self._grouping:
            self._grouping.append(self._WELL_COLUMN)
        if self._grouping and self._PLANE not in self._grouping:
            self._grouping.append(self._PLANE)
        self._fields_by_well = self._calculate_fields_count_by_well(fields_by_well)
        if z_planes:
            self._z_planes = [str(z_plane) for z_plane in z_planes]
            self._squashed_z_planes_column_value = 'Projection %s' % '-'.join(self._z_planes)

    def get_calculation_specs_as_json(self):
        return [spec.to_json() for spec in self._calculation_specs]

    def get_grouping(self):
        return self._grouping

    def post_run(self, workspace):
        super(DefineResults, self).post_run(workspace)
        result_dataframe = self.calculate_results()
        result_dataframe.to_csv(self._build_object_csv_path('Results'))
        result_dataframe.to_excel(self._build_object_xlsx_path('Results'))

    def _do_operation(self, spec: CalculationSpec, result_data_dict):
        operation = spec.operation
        if operation == 'Number of Objects':
            self._process_number_of_objects(result_data_dict, spec)
        elif operation == 'Total Area':
            self._process_total_area(result_data_dict, spec)
        elif operation == 'Relative Intensity':
            self._process_relative_intensity(result_data_dict, spec)
        elif operation == 'Number of':
            self._process_column_from_primary_dataframe(result_data_dict, spec, self._children_count_column(spec))
        elif operation == 'Number of per Area':
            self._process_number_of_secondary_per_area(result_data_dict, spec)
        elif operation == 'Mean Intensity':
            self._process_intensity_column_from_primary_dataframe(result_data_dict, spec, self._MEAN_INTENSITY)
        elif operation == 'Background Intensity':
            self._process_intensity_column_from_primary_dataframe(result_data_dict, spec, self._MEAN_EDGE_INTENSITY)
        elif operation == 'Corrected Intensity':
            self._process_corrected_intensity(result_data_dict, spec)
        elif operation == 'Relative Object Intensity':
            self._process_relative_object_intensity(result_data_dict, spec)
        elif operation == 'Uncorrected Peak Intensity':
            self._process_intensity_column_from_primary_dataframe(result_data_dict, spec, self._MAX_INTENSITY)
        elif operation == 'Contrast':
            self._process_contrast(result_data_dict, spec)
        elif operation == 'Area':
            self._process_column_from_primary_dataframe(result_data_dict, spec, self._AREA_SHAPE_AREA)
        elif operation == 'Region Intensity':
            self._process_region_intensity(result_data_dict, spec)
        elif operation == 'To Region Intensity':
            self._process_secondary_to_primary_region_intensity(result_data_dict, spec)
        else:
            raise RuntimeError('Unsupported operation [{}]'.format(operation))

    def _do_formula(self, spec: FormulaSpec, result_data_dict):
        local_data_dict = {}
        variables_dict = {}
        for prop in spec.properties:
            self._do_operation(prop, local_data_dict)
            if prop.column_operation_name is not None:
                if len(prop.stat_functions) == 1:
                    variables_dict[prop.column_operation_name] = self._build_feature_name(prop, prop.stat_functions[0])
                else:
                    variables_dict[prop.column_operation_name] = self._build_feature_name(prop)
        def _is_float(o):
            try:
                float(o)
                return True
            except Exception as e:
                return False
        def _process_formula(dataset, expression):
            if isinstance(expression, list) and len(expression) == 1:
                return _process_formula(dataset, expression[0])
            if isinstance(expression, list) and len(expression) == 3:
                left, operator, right = expression
                left_value = _process_formula(dataset, left)
                right_value = _process_formula(dataset, right)
                if left_value is None or right_value is None:
                    return None
                if operator == '*':
                    return left_value * right_value
                elif operator == '/':
                    if right_value == 0:
                        return None
                    return left_value / right_value
                elif operator == '-':
                    return left_value - right_value
                elif operator == '+':
                    return left_value + right_value
                elif operator == '^':
                    return pow(left_value, right_value)
                else:
                    raise RuntimeError('Unsupported operator [{}]'.format(operator))
            elif _is_float(expression):
                return float(expression)
            elif isinstance(expression, str):
                return float(dataset[variables_dict[expression]])
            else:
                raise RuntimeError('Unsupported expression {}'.format(expression))
        for group_name, group_calculation_results in local_data_dict.items():
            value = _process_formula(group_calculation_results, spec.expression)
            self._append_spec_value_to_results(result_data_dict, group_name, spec.column_operation_name, value)

    def calculate_results(self):
        result_data_dict = {}
        for spec in self._calculation_specs:
            if spec.formula:
                self._do_formula(spec, result_data_dict)
            else:
                self._do_operation(spec, result_data_dict)
        result_index = list()
        result_data_list = list()
        well_y_index = self._grouping.index(self._WELL_COLUMN)
        well_x_index = self._grouping.index(self._WELL_ROW)
        plane_index = self._grouping.index(self._PLANE)
        for group_name, group_calculation_results in result_data_dict.items():
            group_name = self._build_group_name_with_squashed_z_planes(group_name, plane_index)
            result_index.append(group_name)
            result_data_list.append(group_calculation_results)
            well_key = (group_name[well_y_index], group_name[well_x_index])
            fields_count = self._fields_by_well.get(well_key)
            group_calculation_results.update({'Number of Fields': fields_count})
        return pandas.DataFrame(result_data_list, index=self._prepare_df_index(result_index))

    def _prepare_df_index(self, result_indices):
        if len(self._grouping) == 1:
            index_name = self._grouping[0]
            index = Index(result_indices)
            index.name = index_name
            return index
        return MultiIndex.from_tuples(result_indices, names=self._grouping)

    def _process_number_of_objects(self, result_data_dict, spec):
        object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        grouping_datasets_dictionary = self._build_groupings(object_dataframe)
        feature_name = self._build_feature_name(spec)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            value = int(grouping_dataframe.get('ObjectNumber').count())
            self._append_spec_value_to_results(result_data_dict, grouping_value, feature_name, value)

    def _process_total_area(self, result_data_dict, spec: CalculationSpec):
        object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.secondary))
        grouping_datasets_dictionary = self._build_groupings(object_dataframe)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            secondary_by_primary_grouping_result = self._group_secondary_by_primary_parent(grouping_dataframe, spec)
            secondary_objects_areas = list()
            for secondary_object_dataframe in secondary_by_primary_grouping_result.values():
                secondary_objects_areas.append(int(secondary_object_dataframe.get(self._AREA_SHAPE_AREA).sum()))
            secondary_object_areas_dataseries = pandas.Series(data=secondary_objects_areas)
            self._calculate_and_add_all_stat_functions(result_data_dict, secondary_object_areas_dataseries,
                                                       grouping_value, spec)

    def _process_relative_intensity(self, result_data_dict, spec: CalculationSpec):
        secondary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.secondary))
        secondary_grouping_datasets_dictionary = self._build_groupings(secondary_object_dataframe)
        secondary_integrated_intensity_series = {}
        intensity_channel_name = ''
        for grouping_value, grouping_dataframe in secondary_grouping_datasets_dictionary.items():
            secondary_by_primary_grouping_result = self._group_secondary_by_primary_parent(grouping_dataframe, spec)
            secondary_objects_intensity_integrated = list()
            for secondary_object_dataframe in secondary_by_primary_grouping_result.values():
                intensity_channel_name = self._extract_intensity_channel_name(secondary_object_dataframe,
                                                                              self._INTEGRATED_INTENSITY)
                a = float(secondary_object_dataframe.get(self._INTEGRATED_INTENSITY + intensity_channel_name).sum())
                b = float(secondary_object_dataframe.get(self._MEAN_EDGE_INTENSITY + intensity_channel_name).sum())
                secondary_objects_intensity_integrated.append(a - b)
            secondary_integrated_intensity_series[grouping_value] = \
                pandas.Series(secondary_objects_intensity_integrated)
        primary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        primary_grouping_datasets_dictionary = self._build_groupings(primary_object_dataframe)
        for grouping_value, grouping_dataframe in primary_grouping_datasets_dictionary.items():
            grouping_dataframe = grouping_dataframe[grouping_dataframe[self._children_count_column(spec)] != 0]
            primary_objects_integrated_intensity_dataseries = grouping_dataframe.get(self._INTEGRATED_INTENSITY
                                                                                    + intensity_channel_name)
            secondary_integrated_intensity_dataseries = secondary_integrated_intensity_series[grouping_value]
            relative_intensity_dataseries = secondary_integrated_intensity_dataseries / primary_objects_integrated_intensity_dataseries
            self._calculate_and_add_all_stat_functions(result_data_dict, relative_intensity_dataseries,
                                                       grouping_value, spec)

    def _process_column_from_primary_dataframe(self, result_data_dict, spec: CalculationSpec, target_column):
        primary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        grouping_datasets_dictionary = self._build_groupings(primary_object_dataframe)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            column_dataseries = grouping_dataframe.get(target_column)
            self._calculate_and_add_all_stat_functions(result_data_dict, column_dataseries, grouping_value, spec)
    
    def _process_number_of_secondary_per_area(self, result_data_dict, spec):
        object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        grouping_datasets_dictionary = self._build_groupings(object_dataframe)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            secondary_objects_count_dataseries = grouping_dataframe.get(self._children_count_column(spec))
            primary_objects_area_dataseries = grouping_dataframe.get(self._AREA_SHAPE_AREA)
            secondary_objects_per_area_dataseries = secondary_objects_count_dataseries / primary_objects_area_dataseries
            self._calculate_and_add_all_stat_functions(result_data_dict, secondary_objects_per_area_dataseries,
                                                       grouping_value, spec)
    
    def _children_count_column(self, spec):
        return 'Children_{}_Count'.format(spec.secondary)

    def _group_secondary_by_primary_parent(self, grouping_dataframe, spec):
        secondary_by_primary_grouping_result = self._build_grouping(grouping_dataframe, 'Parent_' + spec.primary)
        if 0 in secondary_by_primary_grouping_result:
            secondary_by_primary_grouping_result.pop(0)
        return secondary_by_primary_grouping_result

    def _calculate_and_add_all_stat_functions(self, result_data_dict, target_dataseries, grouping_value, spec):
        for function_name in spec.stat_functions:
            feature_name = self._build_feature_name(spec, function_name)
            value = self._calculate_stat_for_dataframe(target_dataseries, function_name)
            self._append_spec_value_to_results(result_data_dict, grouping_value, feature_name, value)

    def _calculate_stat_for_dataframe(self, frame: pandas.Series, func_name):
        if func_name == self._MAX_STAT_FUNC_KEY:
            return float(frame.max())
        elif func_name == self._MIN_STAT_FUNC_KEY:
            return float(frame.min())
        elif func_name == self._MEAN_STAT_FUNC_KEY:
            return float(frame.mean())
        elif func_name == self._MEDIAN_STAT_FUNC_KEY:
            return float(frame.median())
        elif func_name == self._SUM_STAT_FUNC_KEY:
            return float(frame.sum())
        elif func_name == self._STANDARD_DEV_STAT_FUNC_KEY:
            return float(frame.std())
        elif func_name == self._COEFFICIENT_OF_VARIATION_STAT_FUNC_KEY:
            std = float(frame.std())
            mean = float(frame.mean())
            return std / mean * 100
        else:
            return float('nan')

    def _build_grouping(self, object_dataframe, grouping_key):
        """
        Method to build a grouping from a dataset
        :param object_dataframe: input dataframe
        :param grouping_key: str, specifying a grouping attribute.
        If nothing is specified - use general grouping metadata column name
        :return: dictionary, where keys are grouping buckets keys and values are corresponding dataframes
        """
        return self._extract_grouping_entries(object_dataframe.groupby(grouping_key))

    def _build_groupings(self, object_dataframe):
        """
        Method to build a grouping from a dataset
        :param object_dataframe: input dataframe
        If nothing is specified - use general grouping metadata column name
        :return: dictionary, where keys are grouping buckets keys and values are corresponding dataframes
        """
        grouping_keys = [self._METADATA_COLUMN_PREFIX + group for group in self._grouping]
        return self._extract_grouping_entries(object_dataframe.groupby(grouping_keys))

    def _append_spec_value_to_results(self, result_data_dict, general_grouping_value, feature_name, value):
        group_calculation_results = result_data_dict.get(general_grouping_value)
        if group_calculation_results is None:
            group_calculation_results = {}
        group_calculation_results[feature_name] = value
        result_data_dict[general_grouping_value] = group_calculation_results

    def _extract_grouping_entries(self, grouping_result: pandas.core.groupby.generic.DataFrameGroupBy):
        return {grouping_key: grouping_result.get_group(grouping_key) for grouping_key in grouping_result.groups.keys()}

    def _extract_intensity_channel_name(self, dataframe, intensity_column):
        for key in dataframe.keys():
            if key.startswith(intensity_column + '_'):
                return key[len(intensity_column):]
        return ''

    def _extract_group_calc_results(self, result_data_dict, group_name):
        group_calculation_results = result_data_dict.get(group_name)
        if group_calculation_results is None:
            group_calculation_results = {}
        return group_calculation_results

    def _build_object_csv_path(self, obj_name):
        return os.path.join(self.directory.custom_path, obj_name + '.csv')

    def _build_object_xlsx_path(self, obj_name):
        return os.path.join(self.directory.custom_path, obj_name + '.xlsx')

    def _build_feature_name(self, spec: CalculationSpec, stat_func_name=None):
        if spec.column_operation_name is not None:
            name = spec.column_operation_name
        elif spec.requires_secondary():
            name = '{} - '.format(spec.primary)
            if spec.operation == 'Total Area' or spec.operation == 'Relative Intensity':
                name = name + '{} of {}'.format(spec.operation, spec.secondary)
            elif spec.operation == 'Number of':
                name = name + '{} {}'.format(spec.operation, spec.secondary)
            elif spec.operation == 'Number of per Area':
                name = name + 'Number of {} per Area'.format(spec.secondary)
            else:
                name = '{} - {} To {} Region Intensity'.format(spec.secondary, spec.secondary, spec.primary)
        else:
            name = '{} - {}'.format(spec.primary, spec.operation)
        if stat_func_name is not None:
            name = name + ' - {}'.format(stat_func_name)
        return name + ' per Well'

    def _process_primary_object_intensity(self, result_data_dict, spec, extract_intensity_lambda):
        primary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        intensity_channel_name = self._extract_intensity_channel_name(primary_object_dataframe, self._MEAN_INTENSITY)
        grouping_datasets_dictionary = self._build_groupings(primary_object_dataframe)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            calculated_intensity = extract_intensity_lambda(grouping_dataframe, intensity_channel_name)
            self._calculate_and_add_all_stat_functions(result_data_dict, calculated_intensity, grouping_value, spec)

    def _process_intensity_column_from_primary_dataframe(self, result_data_dict, spec: CalculationSpec, target_column):
        target_column_extractor = lambda frame, intensity_channel: frame.get(target_column + intensity_channel)
        self._process_primary_object_intensity(result_data_dict, spec, target_column_extractor)

    def _calculate_frame_corrected_intensity(self, grouping_dataframe, intensity_channel_name):
        mean_intensity_dataseries = grouping_dataframe.get(self._MEAN_INTENSITY + intensity_channel_name)
        background_intensity_dataseries = grouping_dataframe.get(self._MEAN_EDGE_INTENSITY + intensity_channel_name)
        return mean_intensity_dataseries - background_intensity_dataseries

    def _process_corrected_intensity(self, result_data_dict, spec):
        self._process_primary_object_intensity(result_data_dict, spec, self._calculate_frame_corrected_intensity)

    def _calculate_frame_relative_object_intensity(self, grouping_dataframe, intensity_channel_name):
        mean_intensity_dataseries = grouping_dataframe.get(self._MEAN_INTENSITY + intensity_channel_name)
        background_intensity_dataseries = grouping_dataframe.get(self._MEAN_EDGE_INTENSITY + intensity_channel_name)
        relative_object_intensity = mean_intensity_dataseries - background_intensity_dataseries
        return relative_object_intensity / mean_intensity_dataseries

    def _process_relative_object_intensity(self, result_data_dict, spec):
        self._process_primary_object_intensity(result_data_dict, spec, self._calculate_frame_relative_object_intensity)

    def _calculate_frame_contrast(self, grouping_dataframe, intensity_channel_name):
        max_intensity_dataseries = grouping_dataframe.get(self._MAX_INTENSITY + intensity_channel_name)
        background_intensity_dataseries = grouping_dataframe.get(self._MEAN_EDGE_INTENSITY + intensity_channel_name)
        negate_intensity = max_intensity_dataseries - background_intensity_dataseries
        sum_intensity = max_intensity_dataseries + background_intensity_dataseries
        return negate_intensity / sum_intensity

    def _process_contrast(self, result_data_dict, spec):
        self._process_primary_object_intensity(result_data_dict, spec, self._calculate_frame_contrast)

    def _process_region_intensity(self, result_data_dict, spec):
        primary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        intensity_channel_name = self._extract_intensity_channel_name(primary_object_dataframe, self._MEAN_INTENSITY)
        grouping_datasets_dictionary = self._build_groupings(primary_object_dataframe)
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            grouping_dataframe = grouping_dataframe[grouping_dataframe[self._children_count_column(spec)] != 0]
            primary_objects_mean_intensity = grouping_dataframe.get(self._MEAN_INTENSITY + intensity_channel_name)
            self._calculate_and_add_all_stat_functions(result_data_dict, primary_objects_mean_intensity, grouping_value,
                                                       spec)

    def _process_secondary_to_primary_region_intensity(self, result_data_dict, spec):
        primary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.primary))
        intensity_channel_name = self._extract_intensity_channel_name(primary_object_dataframe, self._MEAN_INTENSITY)
        mean_intensity_column = self._MEAN_INTENSITY + intensity_channel_name
        primary_object_dataframe = primary_object_dataframe[primary_object_dataframe[self._children_count_column(spec)] != 0]
        primary_objects_intensities = {}
        for idx, row in primary_object_dataframe.iterrows():
            region_intensity = row[mean_intensity_column] 
            primary_objects_intensities['{}-{}'.format(row.ImageNumber, row.ObjectNumber)] = region_intensity
        secondary_object_dataframe = pandas.read_csv(self._build_object_csv_path(spec.secondary))
        grouping_datasets_dictionary = self._build_groupings(secondary_object_dataframe)
        primary_parent_column_name = 'Parent_' + spec.primary
        for grouping_value, grouping_dataframe in grouping_datasets_dictionary.items():
            grouping_dataframe = grouping_dataframe[grouping_dataframe[primary_parent_column_name] != 0]
            series_list = [child_intensity / primary_objects_intensities['{}-{}'.format(img_num, parent_num)]
                           for img_num, parent_num, child_intensity
                           in zip(grouping_dataframe['ImageNumber'],
                                  grouping_dataframe[primary_parent_column_name],
                                  grouping_dataframe[mean_intensity_column])]
            secondary_to_primary_region_intensity_dataseries = pandas.Series(data=series_list)
            self._calculate_and_add_all_stat_functions(
                result_data_dict, secondary_to_primary_region_intensity_dataseries, grouping_value, spec)

    def _build_group_name_with_squashed_z_planes(self, group_name, plane_index):
        if self._squashed_z_planes_column_value and str(group_name[plane_index]) in self._z_planes:
            return tuple(self._squashed_z_planes_column_value if i == plane_index else group_name[i]
                         for i in range(len(group_name)))
        else:
            return group_name

    @staticmethod
    def _calculate_fields_count_by_well(fields_by_well: dict):
        result = {}
        for well_pair, fields in fields_by_well.items():
            result.update({well_pair: len(fields)})
        return result
