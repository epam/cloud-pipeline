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

import argparse

from pipeline import Logger

METRICS_FIELDS = ["Sample",
                  "Chromosome",
                  "Position",
                  "VariantID",
                  "ReferenceAllele",
                  "AlternativeAllele",
                  "Quality",
                  "Filter",
                  "VariantType",
                  "DP",
                  "VD",
                  "AD",
                  "AF",
                  "Annotation",
                  "Impact",
                  "Gene"]


class CollectVariantsMetrics(object):

    def __init__(self, task, vcf, output_file):
        self.task = task
        self.vcf_file = vcf
        self.output_file = output_file

    def run(self):
        Logger.info("Reading %s file to collect variants metrics." % self.vcf_file, task_name=self.task)
        with open(self.output_file, 'w+') as output, open(self.vcf_file, 'r') as vcf:
            self.__write_header(output)
            lines_started = False
            for vcf_line in vcf.readlines():
                if lines_started and vcf_line:
                    self.__process_variant(output, vcf_line)
                elif vcf_line.startswith("#CHROM"):
                    lines_started = True

    def __process_variant(self, output, vcf_line):
        variant_fields = self.__parse_vcf_line(vcf_line)
        for field in METRICS_FIELDS:
            value = "."
            if field in variant_fields:
                value = variant_fields[field]
            output.write(str(value) + "\t")
        output.write("\n")

    def __parse_vcf_line(self, vcf_line):
        variant_data = {}
        chunks = vcf_line.split()
        variant_data["Chromosome"] = chunks[0]
        variant_data["Position"] = chunks[1]
        variant_data["VariantID"] = chunks[2]
        variant_data["ReferenceAllele"] = chunks[3]
        variant_data["AlternativeAllele"] = chunks[4]
        variant_data["Quality"] = chunks[5]
        variant_data["Filter"] = chunks[6]
        self.__fill_info_field(variant_data, chunks[7])
        return variant_data

    def __fill_info_field(self, variant_data, info_field):
        fields = {}
        for field in info_field.split(";"):
            key_value = field.split("=")
            fields[key_value[0]] = key_value[1]
        self.__set_value_if_present(variant_data, fields, "Sample", "SAMPLE")
        self.__set_value_if_present(variant_data, fields, "VariantType", "TYPE")
        self.__set_value_if_present(variant_data, fields, "DP", "DP")
        self.__set_value_if_present(variant_data, fields, "VD", "VD")
        self.__set_value_if_present(variant_data, fields, "AD", "AD")
        self.__set_value_if_present(variant_data, fields, "AF", "AF")
        if "ANN" in fields:
            self.__fill_ann_data(variant_data, fields["ANN"])

    def __fill_ann_data(self, variant_data, ann_field):
        chunks = ann_field.split("|")
        variant_data["Annotation"] = chunks[1]
        variant_data["Impact"] = chunks[2]
        variant_data["Gene"] = chunks[3]

    def __set_value_if_present(self, variant_data, fields_data, metric_name, field_name):
        if field_name in fields_data:
            variant_data[metric_name] = fields_data[field_name]

    def __write_header(self, output):
        for field in METRICS_FIELDS:
            output.write(field + "\t")
        output.write("\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--task', required=True)
    parser.add_argument('--vcf', required=True)
    parser.add_argument('--output-file', required=True)
    args = parser.parse_args()
    CollectVariantsMetrics(args.task, args.vcf, args.output_file).run()


if __name__ == '__main__':
    main()
