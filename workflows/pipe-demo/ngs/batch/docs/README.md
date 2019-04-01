# Batch Pipeline

## Overview
Batch pipeline launches and orchestrates secondary analysis of NGS data. This pipeline includes two steps, each represented as a separate pipeline:
- `Demultiplexing` - converts raw sequencing data to Fastq files for further processing
- `Analytical processing` - performs secondary analysis for each sample, as a result generates number of output files: bam, vcf and various metrics results.

Batch pipeline launches `Demultiplexing pipeline`, waits until it's successful completion and launches `Analytical pipeline` for each of the samples present in the `SAMPLE_SHEET`.

### Batch Pipeline parameters:
- `MACHINE_RUN_FOLDER` [required] - path to raw sequencing data folder (Illumina Machine Run Folder)
- `SAMPLE_SHEET` [required] - path to Illumina Sample Sheet file
- `ANALYSIS_FOLDER` [required] - path to folder, where results of demultiplexing and analytical processing shall be stored
- `DEMULTIPLEX_PIPELINE` [required] - name of the pipeline that shall be launched to perform `Demultiplexing` step
- `DEMULTIPLEX_VERSION` [required] - version of the pipeline that shall be launched to perform `Demultiplexing` step
- `DEMULTIPLEX_INSTANCE` [optional] - instance size that shall be used for  `Demultiplexing` step, default instance size specified for `DEMULTIPLEX_VERSION` will be used, if option is not provided
- `DEMULTIPLEX_DISK` [optional] - disk size that shall be used for  `Demultiplexing` step, default disk size specified for `DEMULTIPLEX_VERSION` will be used, if option is not provided
- `ANALYTICAL_PIPELINE` [required] - name of the pipeline that shall be launched to perform `Analytical processing` step
- `ANALYTICAL_VERSION` [required] - version of the pipeline that shall be launched to perform `Analytical processing` step
- `ANALYTICAL_INSTANCE` [optional] - instance size that shall be used for  `Analytical processing` step, default instance size specified for `ANALYTICAL_VERSION` will be used, if option is not provided
- `ANALYTICAL_DISK` [optional] - disk size that shall be used for  `Analytical processing` step, default disk size specified for `ANALYTICAL_VERSION` will be used, if option is not provided