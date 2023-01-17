# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import xml.etree.ElementTree as ET
import json
import os
import pandas
from utils import HcsParsingUtils, log_run_info
from HTMLParser import HTMLParser


ID = 'Id'
RESULT_FILE_NAME = 'Results'
WELL_ROW = "WellRow"
WELL_COLUMN = "WellColumn"
PLANE = "Plane"
TIMEPOINT = "Timepoint"
DEFAULTS = [WELL_ROW, WELL_COLUMN, PLANE, TIMEPOINT]


class HcsFileEvalProcessor:

    def __init__(self, hcs_eval_dir, hcs_results_dir):
        """
        :param hcs_eval_dir: path to 'eval' folder
        :param hcs_results_dir: path to img results dir
        """
        self.hcs_eval_dir = hcs_eval_dir
        self.hcs_results_dir = hcs_results_dir

    def log_processing_info(self, message):
        log_run_info('[{}] {}'.format(self.hcs_eval_dir, message))

    def parse_evaluations(self):
        self.log_processing_info("Processing started")
        dir_names = self.get_evals_directories()
        for dir_name in dir_names:
            evaluation_path = os.path.join(self.hcs_results_dir, "eval", dir_name)
            try:
                os.makedirs(evaluation_path)
            except OSError:
                pass
            source_evaluations_path = os.path.join(self.hcs_eval_dir, dir_name)

            root_xml_file, root_keyword_file = self.get_evals_files(source_evaluations_path)
            if not root_xml_file:
                self.log_processing_info("Evaluation xml file is missing '%s'" % root_xml_file)
            if not root_keyword_file:
                self.log_processing_info("Evaluation keyword file is missing '%s'" % root_keyword_file)

            self.build_evaluation_description(root_xml_file, evaluation_path)

            evaluations_map = self.build_evaluations_spec(root_xml_file, root_keyword_file)
            if evaluations_map:
                with open(os.path.join(evaluation_path, 'spec.json'), "w") as file_stream:
                    json.dump(evaluations_map, file_stream)
            else:
                self.log_processing_info("No evaluation specification can be constructed")

            plate_results_path = os.path.join(source_evaluations_path, 'plateresult')
            plate_results_xml_file, _ = self.get_evals_files(plate_results_path)
            if not plate_results_xml_file:
                self.log_processing_info("Plate results xml file is missing '%s'" % plate_results_path)

            self.build_evaluation_results(plate_results_xml_file, evaluation_path)

            self.log_processing_info("Finished '%s' directory processing" % dir_name)
        self.log_processing_info("Processing finished")

    @staticmethod
    def parse_xml_array(selection, schema, xml_element_name):
        element = selection.find(schema + xml_element_name)
        if element is None:
            return []
        xml_items = element.findall(schema + ID)
        if not xml_items:
            return []
        json_items = []
        for item in xml_items:
            json_items.append(item.text)
        return json_items

    def build_evaluations_spec(self, xml_path, keyword_path):
        evaluations_map = {}
        if xml_path:
            root = ET.parse(xml_path).getroot()
            schema = HcsParsingUtils.extract_xml_schema(root)
            selection = root.find(schema + 'Selection')
            xml_wells = selection.find(schema + 'Wells').findall(schema + 'Well')
            json_wells = []
            for well in xml_wells:
                json_wells.append(
                    {
                        "x": well.find(schema + 'Col').text,
                        "y": well.find(schema + 'Row').text
                    }
                )
            evaluations_map.update({"wells": json_wells})

            evaluations_map.update({"fields": self.parse_xml_array(selection, schema, 'Fields')})
            evaluations_map.update({"planes": self.parse_xml_array(selection, schema, 'Planes')})
            evaluations_map.update({"timepoints": self.parse_xml_array(selection, schema, 'KineticRepetitions')})

        if keyword_path:
            with open(keyword_path, 'r') as keyword_file:
                keyword_data = "".join(keyword_file.readlines()[1:-2])
            if keyword_data:
                keyword_json_data = json.loads(keyword_data)
                evaluations_map.update({"name": keyword_json_data['NAME']})
                evaluations_map.update({"owner": keyword_json_data['OWNER']})
                evaluations_map.update({"date": keyword_json_data['DATE']})
                evaluations_map.update({"channels": keyword_json_data['CHANNEL']})
            else:
                self.log_processing_info("No keyword data '%s' fond" % keyword_path)

        return evaluations_map

    @staticmethod
    def fill_defaults(attribute_value, result_data, data_name):
        if attribute_value:
            if data_name not in result_data:
                result_data.update({data_name: list()})
            result_data.get(data_name).append(attribute_value)
        return attribute_value

    def build_evaluation_results(self, plate_results_xml_path, result_dir):
        if not plate_results_xml_path:
            return
        root = ET.parse(plate_results_xml_path).getroot()
        schema = HcsParsingUtils.extract_xml_schema(root)

        xml_parameters = root.find(schema + 'ParameterAnnotations').findall(schema + 'Parameter')
        header = {}
        for xml_parameter in xml_parameters:
            header.update({xml_parameter.attrib.get('id'): xml_parameter.attrib.get('name').encode('utf-8')})

        result_data = {}
        xml_results = root.findall(schema + 'Results')
        for i in range(0, len(xml_results)):
            results = xml_results[i]
            xml_result = results.findall(schema + 'Result')

            self.fill_defaults(results.attrib.get('Row'), result_data, WELL_ROW)
            self.fill_defaults(results.attrib.get('Col'), result_data, WELL_COLUMN)
            self.fill_defaults(results.attrib.get('PlaneID'), result_data, PLANE)
            self.fill_defaults(results.attrib.get('TimepointID'), result_data, TIMEPOINT)

            for result in xml_result:
                par_id = result.attrib['parID']
                result_values = result.findall(schema + "value")
                for result_value in result_values:
                    child_par_id = result_value.attrib.get('parID')
                    if not child_par_id:
                        kind = result_value.attrib.get('kind')
                        column_name = "%s - %s per Well" % (header.get(par_id), kind)
                    else:
                        column_name = header.get(child_par_id)
                    value = result_value.text
                    if not result_data.get(column_name):
                        result_data.update({column_name: list()})
                        # fill missing values blank if some column unexpectedly appears
                        for j in range(0, i):
                            result_data.get(column_name).append("")
                    result_data.get(column_name).append(value)

        if not result_data:
            self.log_processing_info("No evaluation results found")
            return

        df = pandas.DataFrame()
        # place mandatory fields at the beginning
        for column_name, column_values in result_data.items():
            if column_name in DEFAULTS:
                df[column_name] = column_values

        for column_name, column_values in result_data.items():
            if column_name not in DEFAULTS:
                df[column_name] = column_values

        df.to_csv(os.path.join(result_dir, "%s.csv" % RESULT_FILE_NAME), index=False)
        df.to_excel(os.path.join(result_dir, "%s.xlsx" % RESULT_FILE_NAME), index=False)

    def get_evals_directories(self):
        dir_names = [dirs for _, dirs, _ in os.walk(self.hcs_eval_dir)]
        if not dir_names:
            return []
        return dir_names[0]

    @staticmethod
    def get_evals_files(folder):
        files = [files for _, _, files in os.walk(folder)]
        if not files:
            return None, None
        root_files = files[0]
        if not root_files:
            return None, None

        xml_file = None
        keyword_file = None
        for file_name in root_files:
            if file_name.endswith(".xml"):
                xml_file = os.path.join(folder, file_name)
                continue
            if file_name.endswith(".kw.txt"):
                keyword_file = os.path.join(folder, file_name)
        return xml_file, keyword_file

    def get_evaluation_description(self, xml_path):
        if not xml_path:
            return None
        root = ET.parse(xml_path).getroot()
        schema = HcsParsingUtils.extract_xml_schema(root)
        analysis_encoded_element = root.find(schema + 'AnalysisEncoded')
        if analysis_encoded_element is not None:
            analysis_encoded = analysis_encoded_element.text
            if analysis_encoded:
                return HTMLParser().unescape(analysis_encoded)
        else:
            self.log_processing_info("No analysis information found in %s" % xml_path)
        return None

    def build_evaluation_description(self, root_xml_file, evaluation_path):
        try:
            analysis_description = self.get_evaluation_description(root_xml_file)
            if analysis_description:
                with open(os.path.join(evaluation_path, "AnalysisFile.aas"), "w") as file_stream:
                    file_stream.write(analysis_description.encode('utf-8'))
            else:
                self.log_processing_info("No evaluation description can be constructed")
        except Exception as e:
            self.log_processing_info(e.message)
