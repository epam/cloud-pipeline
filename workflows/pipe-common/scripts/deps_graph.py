# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
# limitations under the License.

import sys
import json
from importlib import import_module
import luigi
from luigi.cmdline_parser import CmdlineParser


def print_output(task):
    result = ''
    try:
        for out in task.output():
            result += out.path + ';'
    except TypeError:
        if hasattr(task.output(), 'path'):
            result += task.output().path
    return result


def print_input_output(task):
    # input for task is output of it's requirements
    input_str = 'IN:'
    try:
        for require in task.requires():
            input_str += print_output(require) + ';'
    except TypeError:
        input_str += print_output(task.requires())
    print(input_str)
    output_str = 'OUT:'
    output_str += print_output(task)
    print(output_str)
    tool = 'TOOL:'
    if hasattr(task, 'tool_image'):
        tool += task.tool_image
    print(tool)


def print_deps(task):
    deps = task.deps()
    for dep in deps:
        representation = task.__repr__() + " => "
        if not hasattr(dep, 'helper') or not dep.helper:
            representation += dep.__repr__()
        print(representation)
        print_input_output(dep)
        print_deps(dep)


def get_parameters(args):
    with CmdlineParser.global_instance(args) as cp:
        cls = cp._get_task_cls()
        params = cls.get_params()
        run_script = []
        run_script.extend(args)
        for param in params:
            run_script.append('--' + param[0].replace('_', '-'))
            if type(param[1]) == luigi.IntParameter:
                run_script.append('1')
            elif type(param[1]) != luigi.BoolParameter:
                run_script.append('&' + param[0] + '&')
        return run_script


def get_graph(args):
    with CmdlineParser.global_instance(args) as cp:
        task = cp.get_task_obj()
        print(task.__repr__())
        print_input_output(task)
        print_deps(task)


def parse_config(config, config_name):
    if type(config) == dict:
        return get_main_file_class(config)
    else:
        for item in config:
            if not config_name or item['name'] == config_name:
                return get_main_file_class(item['configuration'])
        return get_main_file_class(config[0]['configuration'])


def get_main_file_class(config):
    return config['main_file'][:-3], config['main_class']


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise RuntimeError('Path to repository and configuration json are required.')
    path_to_json = sys.argv[1] + '/' + sys.argv[2]
    config_name = None
    if len(sys.argv) == 4:
        config_name = sys.argv[3]
    with open(path_to_json) as configuration:
        config = json.load(configuration)
    main_file, main_class = parse_config(config, config_name)
    folder = sys.argv[1] + '/src/'
    sys.path.insert(0, folder)
    import_module(main_file)
    classWithParams = get_parameters([main_class])
    get_graph(classWithParams)
