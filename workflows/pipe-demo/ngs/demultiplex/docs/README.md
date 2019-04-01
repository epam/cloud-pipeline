# Demultiplexing Pipeline

## Overview
`Demultiplexing Pipeline` converts raw sequencing data from Illumina machine run into FASTQ files. The main (and the most resource consuming) step in this process is Illumina **bcl2fastq** application that performs demultiplexing itself. This step is skipped if FASTQ files are already present in the `MACHINE_RUN_FOLDER`, for example for MiSeq runs executed with "Generate FASTQ" workflow. As as result of `Demultiplexing Pipeline` run sample level FASTQ files are uploaded to `ANALYSIS_FOLDER` for further processing.

## Demultiplexing Pipeline Steps
- `Demultiplex` - runs **bcl2fastq**, if FASTQ files are not present in  `MACHINE_RUN_FOLDER`
- `MergeFastq` - merges generated FASTQ files corresponding to one sample (e.g. divided by lane)
- `CopyFiles` - copies additional files required for further analysis: **SampleSheet**, **InterOp folder**, **undertemined FASTQ files**

## Demultiplexing Pipeline Parameters
- `MACHINE_RUN_FOLDER` [required] - path to folder with Illumina output (raw sequencing data)
- `ANALYSIS_FOLDER` [required] - path to output demultiplexing results
- `SAMPLE_SHEET` [optional] - path to Illumina SampleSheet file, that shall be used for demupltiplexing, if parameter is not provided default SampleSheet path is used: `$MACHINE_RUN_FOLDER/SampleSheet.csv`