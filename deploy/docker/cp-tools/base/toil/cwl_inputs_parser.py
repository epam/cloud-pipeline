# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License
import os
import subprocess
import sys
import yaml
import ruamel.yaml
import csv


VARIABLE_DELIMITER = ","


def not_empty(value):
    return value is not None and value is not {} and value is not []


def str_to_bool(input_value):
    return input_value.lower() in ("true", "t")


def is_optional(key, comments):
    comment = comments.ca.items[key][2].value
    return '(optional)' in comment


def get_parameter_value(env_var_name, optional):
    env_var_value = os.environ.get(env_var_name, None)
    if not env_var_value and not optional:
        raise RuntimeError("Variable '%s' required but not found" % env_var_name)
    return env_var_value


def build_simple_input(key, value, optional):
    parameter_value = get_parameter_value(key, optional)
    if not parameter_value:
        return None
    if type(value) == int:
        return int(parameter_value)
    if type(value) == float:
        return float(parameter_value)
    if type(value) == bool:
        return str_to_bool(parameter_value)
    return parameter_value


def build_simple_inputs(template_value, env_var_values):
    if not env_var_values:
        return None
    if type(template_value) == int:
        return [int(parameter_value) for parameter_value in env_var_values]
    if type(template_value) == float:
        return [float(parameter_value) for parameter_value in env_var_values]
    if type(template_value) == bool:
        return [str_to_bool(parameter_value) for parameter_value in env_var_values]
    return env_var_values


def build_file_or_directory_object(class_value, path_value):
    result = dict()
    result['class'] = class_value

    if type(path_value) == dict:
        if 'path' not in path_value:
            raise RuntimeError('"path" field not found for the {} object'.format(path_value))
        result['path'] = path_value['path']
        if len(path_value.keys()) > 1:
            result['metadata'] = {}
            for metadata_name in path_value.keys():
                if metadata_name != 'path':
                    result['metadata'][metadata_name] = path_value[metadata_name]
    else:
        result['path'] = path_value
    return result

def parse_manifest_file(manifest_file_path):
    if not os.path.isfile(manifest_file_path):
        return [manifest_file_path]

    parsed_items = []
    with open(manifest_file_path) as f:
        parsed_items = [{k.strip(): v.strip() if v else '' for k, v in row.items()}
                            for row in csv.DictReader(f, skipinitialspace=True)]
    return parsed_items

def build_array_input(key, value, optional):
    if not value:
        return None
    template_value = value[0]
    env_var_value = get_parameter_value(key, optional)
    if not env_var_value:
        return None
    env_var_values = [x for x in env_var_value.split(VARIABLE_DELIMITER) if x]
    if type(template_value) == dict:
        class_value = template_value.get('class')
        if not class_value or 'path' not in template_value:
            raise RuntimeError('Unknown type')
        if len(env_var_values) == 1 and env_var_values[0].endswith('.manifest.csv'):
            env_var_values = parse_manifest_file(env_var_values[0])
        return [build_file_or_directory_object(class_value, env_var_value_path) for env_var_value_path
                in env_var_values]
    return build_simple_inputs(template_value, env_var_values)


def build_file_or_directory(key, value, optional):
    path_value = get_parameter_value(key, optional)
    if not path_value:
        return None
    return build_file_or_directory_object(value.get('class'), path_value)


def build_record_input(record_key, record_value, comments):
    result = dict()
    for key, value in record_value.items():
        parameter_value = build_input_item(key, value, comments[record_key])
        if not_empty(parameter_value):
            result[key] = parameter_value
    if len(result) == 0:
        return None
    return result


def build_input_item(key, value, comments):
    optional = is_optional(key, comments)
    if type(value) == dict:
        class_value = value.get('class', None)
        if not class_value:
            return build_record_input(key, value, comments)
        if class_value not in ['File', 'Directory']:
            raise RuntimeError("Unknown type '%s'. Possible values: File, Directory." % class_value)
        return build_file_or_directory(key, value, optional)
    if type(value) == list:
        return build_array_input(key, value, optional)
    return build_simple_input(key, value, optional)


if len(sys.argv) == 1:
    raise Exception('CWL file path shall be specified')
cwl_file = sys.argv[1]
if not os.path.isfile(cwl_file):
    raise Exception('CWL file not found: {}'.format(cwl_file))

inputs_file = None
if len(sys.argv) > 2:
    inputs_file = sys.argv[2]

tmp_inputs_file = None
if len(sys.argv) > 3:
    tmp_inputs_file = sys.argv[3]

subprocess.check_output('cwltool --make-template {} > {}'.format(cwl_file, tmp_inputs_file), shell=True)

with open(tmp_inputs_file, 'r') as stream:
    template_with_comments = ruamel.yaml.round_trip_load(stream)

with open(tmp_inputs_file, 'r') as stream:
    template_data = yaml.safe_load(stream)

result_data = dict()
for data_key, data_value in template_data.items():
    input_item = build_input_item(data_key, data_value, template_with_comments)
    if not_empty(input_item):
        result_data[data_key] = input_item

with open(inputs_file, 'w') as stream:
    yaml.dump(result_data, stream)
