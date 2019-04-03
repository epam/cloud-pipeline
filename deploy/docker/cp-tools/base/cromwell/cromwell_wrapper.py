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

import errno
import json
import os
import re
import sys
from pipeline import TaskStatus, Logger, LoggedCommand
import subprocess

GENERATE_INPUTS_TASK = "GenerateInputsJson"
CROMWELL_TASK = "RunWDL"
VARIABLE_DELIMITER = ","
OPTIONAL_PARAMETER = ["?",  "optional",  "Optional"]
OPTIONS_JSON_TEMPLATE="/cromwell_bin/options.json"
OPTIONS_JSON_INSTANCE="/cromwell_bin/options_instance.json"
OPTIONS_OUTPUT_PATH_VAR="ANALYSIS_DIR"
OPTIONS_OUTPUT_PATH_DEFAULT="/common/"

def get_variable_value(variable_name):
    Logger.log_task_event(GENERATE_INPUTS_TASK, "Getting value of: {}".format(variable_name))

    if not os.environ.get(variable_name):
        return

    variable_value = os.environ.get(env_key)
    if VARIABLE_DELIMITER in variable_value:
        variable_value = [x for x in variable_value.split(VARIABLE_DELIMITER) if x]

    Logger.log_task_event(GENERATE_INPUTS_TASK, "Value of {}:\n{}".format(variable_name, variable_value))

    return variable_value

def generate_options_json():
    if not os.path.isfile(OPTIONS_JSON_TEMPLATE):
        Logger.log_task_event(GENERATE_INPUTS_TASK, "{} options file does not exist".format(OPTIONS_JSON_TEMPLATE))
        return None
    if os.path.isfile(OPTIONS_JSON_INSTANCE):
        os.remove(OPTIONS_JSON_INSTANCE)

    output_path=os.getenv(OPTIONS_OUTPUT_PATH_VAR, OPTIONS_OUTPUT_PATH_DEFAULT)
    options_json_content=None
    with open(OPTIONS_JSON_TEMPLATE, 'r') as options_json:
        options_json_content = options_json.read()
        options_json_content = options_json_content.format(outputs_dir=output_path)
    with open(OPTIONS_JSON_INSTANCE, 'w') as options_inst_json:
        options_inst_json.write(options_json_content)
    
    Logger.log_task_event(GENERATE_INPUTS_TASK, "Workflow options file will be used\n{}".format(options_json_content))

    return OPTIONS_JSON_INSTANCE


inputs_json_file = None

if len(sys.argv) == 1:
    raise Exception('WDL file path shall be specified')

wdl_file = sys.argv[1]

if len(sys.argv) > 2:
    inputs_json_file = sys.argv[2]

if not os.path.isfile(wdl_file):
    raise Exception('WDL file not found: {}'.format(wdl_file))

if inputs_json_file:
    Logger.log_task_event(GENERATE_INPUTS_TASK, "inputs.json file is specified explicitly {}".format(inputs_json_file))
    try:
        input_json_content = None
        with open(inputs_json_file, 'r') as input_json:
            input_json_content = input_json.read()

        Logger.log_task_event(GENERATE_INPUTS_TASK, '{} file contents"\n{}'.format(inputs_json_file, input_json_content), status=TaskStatus.SUCCESS)
    except Exception as e:
        Logger.log_task_event(GENERATE_INPUTS_TASK, str(e), status=TaskStatus.FAILURE)
        exit(1)
else:
    Logger.log_task_event(GENERATE_INPUTS_TASK, "Generating WDL inputs.json from {}".format(wdl_file))

    try:

        subprocess.check_output('java -jar /wdltool_bin/wdltool.jar inputs {} > inputs.json'.format(wdl_file), shell=True)

        template_data = None
        empty_optional = []
        with open('inputs.json', 'r') as input_json:
            template_data = json.load(input_json)
            for key in template_data:
                env_key = re.sub(r'\.', '_', key)
		variable_value = get_variable_value(env_key)
		if not variable_value:
			if any(param in template_data[key] for param in OPTIONAL_PARAMETER):
				empty_optional.append(key)
				continue
   			else:
				raise Exception('Required environment variable {} is absent'.format(key))				
		template_data[key] = variable_value

	    for parameter in empty_optional:
		    template_data.pop(parameter)

        with open('wdl_inputs.json', 'w') as output_json:
            json.dump(template_data, output_json)

        Logger.log_task_event(GENERATE_INPUTS_TASK, 'inputs.json generated:\n{}'.format(json.dumps(template_data)), status=TaskStatus.SUCCESS)
    except Exception as e:
        Logger.log_task_event(GENERATE_INPUTS_TASK, str(e), status=TaskStatus.FAILURE)
        exit(1)

extra_options = generate_options_json()
if extra_options:
    extra_options = '-o ' + extra_options

try:
    cromwell_cmd = 'java -Dconfig.file=/cromwell_bin/cromwell.conf -jar /cromwell_bin/cromwell.jar run {} -i wdl_inputs.json {}'.format(wdl_file, extra_options)

    Logger.log_task_event(CROMWELL_TASK, "Starting Cromwell with a command:\n{}".format(cromwell_cmd))

    LoggedCommand(cromwell_cmd,
                  None,
                  CROMWELL_TASK).execute()

    Logger.log_task_event(CROMWELL_TASK, "Finished cromwell run", status=TaskStatus.SUCCESS)
except Exception as e:
    Logger.log_task_event(CROMWELL_TASK, str(e), status=TaskStatus.FAILURE)
    exit(1)
