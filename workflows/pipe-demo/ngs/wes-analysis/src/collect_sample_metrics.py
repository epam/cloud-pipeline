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
import os
import zipfile

from pipeline import Logger

METRICS_FIELDS = ["SampleName",
                  "StartingReads",
                  "ReadLength",
                  "GC",
                  "QCFailedReads",
                  "ReadsAfterTrim",
                  "AlignedReads",
                  "ReadsOnPanel",
                  "DuplicatesNumber",
                  "ReadsAfterDeDuplication",
                  "MedianInsertSize",
                  "MeanCoverage",
                  "VariantsCount",
                  "SNPCount",
                  "IndelCount"]

SNP_TYPES = ["SNP", "SNV"]
INDEL_TYPES = ["INS", "INSERTION", "DEL", "DELETION", "INDEL"]


class FastQCReader(object):

    def __init__(self):
        pass

    @classmethod
    def read(cls, fastqc_report, task):
        Logger.info("Reading FASTQC report from file: %s." % fastqc_report, task_name=task)
        archive_name = os.path.splitext(os.path.basename(fastqc_report))[0]
        total_reads = 0
        poor_reads = 0
        gc = 0
        read_length = 0
        with zipfile.ZipFile(fastqc_report, 'r') as folder:
            content = folder.read(os.path.join(archive_name, "fastqc_data.txt"))
            for line in content.split("\n"):
                if line.startswith("Total Sequences"):
                    total_reads = int(cls.__get_value(line))
                elif line.startswith("Sequences flagged as poor quality"):
                    poor_reads = int(cls.__get_value(line))
                elif line.startswith("%GC"):
                    gc = cls.__get_value(line)
                elif line.startswith("Sequence length"):
                    read_length = cls.__get_value(line)
                elif line.startswith(">>END_MODULE"):
                    break
        return total_reads, poor_reads, gc, read_length

    @classmethod
    def __get_value(cls, line):
        return line.split("\t")[1]


class FlagstatsReader(object):

    def __init__(self):
        pass

    @classmethod
    def read(cls, report_file, task):
        Logger.info("Reading Flagstats report from file %s." % report_file, task_name=task)
        with open(report_file, 'r') as report:
            line_index = 0
            for line in report.readlines():
                if line_index < 2:
                    line_index += 1
                    continue
                return int(line.split('+')[0].strip())


class MarkDuplicatesReader(object):
    def __init__(self):
        pass

    @classmethod
    def read(cls, report_file, task):
        Logger.info("Reading MarkDuplicates report from file %s." % report_file, task_name=task)
        with open(report_file, 'r') as report:
            data_started = False
            for line in report.readlines():
                if data_started and line:
                    chunks = line.split("\t")
                    # UNPAIRED_READ_DUPLICATES READ_PAIR_DUPLICATES READ_PAIR_OPTICAL_DUPLICATES
                    return int(chunks[5]) + 2 * int(chunks[6]) + 2 * int(chunks[7])
                elif line.startswith("LIBRARY"):
                    data_started = True
        return 0


class InsertSizeMetricsReader(object):
    def __init__(self):
        pass

    @classmethod
    def read(cls, report_file, task):
        Logger.info("Reading InsertSizeMetrics report from file %s." % report_file, task_name=task)
        with open(report_file, 'r') as report:
            data_started = False
            for line in report.readlines():
                if data_started and line:
                    chunks = line.split("\t")
                    # MEDIAN_INSERT_SIZE
                    return int(chunks[0])
                elif line.startswith("MEDIAN_INSERT_SIZE"):
                    data_started = True
        return 0


class CoverageMetricsReader(object):
    def __init__(self):
        pass

    @classmethod
    def read(cls, report_file, task):
        Logger.info("Reading Coverage report from file %s." % report_file, task_name=task)
        total_bases = 0
        total_coverage = 0
        with open(report_file, 'r') as report:
            for line in report.readlines():
                if line:
                    total_bases += 1
                    total_coverage += int(line.split("\t")[2])
        return 0 if total_bases == 0 else total_coverage / total_bases


class CollectSampleMetrics(object):
    def __init__(self, task, folder, file_suffix, sample, output_file):
        self.file_suffix = file_suffix
        self.task = task
        self.folder = folder
        self.sample = sample
        self.output_file = output_file

    def run(self):
        sample_metrics = {}
        self.__fill_sample_data(sample_metrics)
        self.__fill_starting_data(sample_metrics)
        self.__fill_trim_data(sample_metrics)
        self.__fill_align_data(sample_metrics)
        self.__fill_panel_data(sample_metrics)
        self.__fill_duplicate_data(sample_metrics)
        self.__fill_variants_data(sample_metrics)
        self.__fill_metrics_data(sample_metrics)
        self.__print_metrics_file(sample_metrics)

    def __fill_sample_data(self, sample_metrics):
        sample_metrics["SampleName"] = self.sample

    def __fill_starting_data(self, sample_metrics):
        Logger.info("Fetching data from FASTQC Initial reports.", task_name=self.task)
        r1_total_reads, r1_poor_reads, r1_gc, r1_read_length = FastQCReader\
            .read(os.path.join(self.folder, "FastQC_Initial", self.sample + "_R1_fastqc.zip"), self.task)
        r2_total_reads, r2_poor_reads, r2_gc, r2_read_length = FastQCReader\
            .read(os.path.join(self.folder, "FastQC_Initial", self.sample + "_R2_fastqc.zip"), self.task)
        sample_metrics["StartingReads"] = r1_total_reads + r2_total_reads
        sample_metrics["QCFailedReads"] = r1_poor_reads + r2_poor_reads
        sample_metrics["ReadLength"] = r1_read_length
        sample_metrics["GC"] = r1_gc

    def __fill_trim_data(self, sample_metrics):
        Logger.info("Fetching data from FASTQC reports after trimming.", task_name=self.task)
        r1_total_reads, r1_poor_reads, r1_gc, r1_read_length = FastQCReader \
            .read(os.path.join(self.folder, "FastQC_Trimmed", self.file_suffix + ".Trimmomatic.R1.trimmed_fastqc.zip"),
                  self.task)
        r2_total_reads, r2_poor_reads, r2_gc, r2_read_length = FastQCReader \
            .read(os.path.join(self.folder, "FastQC_Trimmed", self.file_suffix + ".Trimmomatic.R2.trimmed_fastqc.zip"),
                  self.task)
        sample_metrics["ReadsAfterTrim"] = r1_total_reads + r2_total_reads

    def __fill_align_data(self, sample_metrics):
        mapped_reads = FlagstatsReader.read(
            os.path.join(self.folder, "BAMStats_Alignment", "flagstat.%s.BAMStats_Alignment.txt" % self.file_suffix),
            self.task)
        sample_metrics["AlignedReads"] = mapped_reads

    def __fill_panel_data(self, sample_metrics):
        mapped_reads = FlagstatsReader.read(
            os.path.join(self.folder, "BAMStats_Panel", "flagstat.%s.BAMStats_Panel.txt" % self.file_suffix),
            self.task)
        sample_metrics["ReadsOnPanel"] = mapped_reads

    def __fill_duplicate_data(self, sample_metrics):
        duplicates = MarkDuplicatesReader.read(
            os.path.join(self.folder, "MarkDuplicates", "markduplicates.%s.MarkDuplicates.txt" % self.file_suffix),
            self.task)
        sample_metrics["DuplicatesNumber"] = duplicates
        mapped_reads = FlagstatsReader.read(
            os.path.join(self.folder, "BAMStats_Final", "flagstat.%s.BAMStats_Final.txt" % self.file_suffix),
            self.task)
        sample_metrics["ReadsAfterDeDuplication"] = mapped_reads - duplicates

    def __fill_metrics_data(self, sample_metrics):
        insert_size = InsertSizeMetricsReader.read(
            os.path.join(self.folder, "InsertSizeMetrics", "metrics.%s.InsertSizeMetrics.txt" % self.file_suffix),
            self.task)
        sample_metrics["MedianInsertSize"] = insert_size
        mean_coverage = CoverageMetricsReader.read(
            os.path.join(self.folder, "CoverageMetrics", "%s.CoverageMetrics.txt" % self.file_suffix),
            self.task)
        sample_metrics["MeanCoverage"] = mean_coverage

    def __fill_variants_data(self, sample_metrics):
        vcf_file = os.path.join(self.folder, "VariantAnnotation", self.file_suffix + ".VariantAnnotation.vcf")
        variant_count = 0
        snp_count = 0
        indel_count = 0
        with open(vcf_file, 'r') as vcf:
            lines_started = False
            for vcf_line in vcf.readlines():
                if lines_started and vcf_line:
                    variant_count += 1
                    type = self.__get_variation_type(vcf_line)
                    if type == "SNP":
                        snp_count += 1
                    elif type == "INDEL":
                        indel_count += 1
                elif vcf_line.startswith("#CHROM"):
                    lines_started = True
        sample_metrics["VariantsCount"] = variant_count
        sample_metrics["SNPCount"] = snp_count
        sample_metrics["IndelCount"] = indel_count

    def __get_variation_type(self, vcf_line):
        fields = vcf_line.split("\t")
        if len(fields) < 8:
            return None
        info_field = fields[7]
        for field in info_field.split(";"):
            key_value = field.split("=")
            if key_value[0].upper() == "TYPE":
                type_value = key_value[1].upper()
                if type_value in SNP_TYPES:
                    return "SNP"
                elif type_value in INDEL_TYPES:
                    return "INDEL"

    def __print_metrics_file(self, sample_metrics):
        with open(self.output_file, 'w+') as output:
            self.__write_header(output)
            self.__write_metrics_values(output, sample_metrics)

    def __write_metrics_values(self, output, sample_metrics):
        for field in METRICS_FIELDS:
            value = "."
            if field in sample_metrics:
                value = sample_metrics[field]
            output.write(str(value) + "\t")
        output.write("\n")

    def __write_header(self, output):
        for field in METRICS_FIELDS:
            output.write(field + "\t")
        output.write("\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--task', required=True)
    parser.add_argument('--folder', required=True)
    parser.add_argument('--file-suffix', required=True)
    parser.add_argument('--sample', required=True)
    parser.add_argument('--output-file', required=True)
    args = parser.parse_args()
    CollectSampleMetrics(args.task, args.folder, args.file_suffix, args.sample, args.output_file).run()


if __name__ == '__main__':
    main()
