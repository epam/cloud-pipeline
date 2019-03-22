# Microsoft Genomics GATK4

Microsoft Genomics offers a cloud implementation of the Burrows-Wheeler Aligner (BWA) and the Genome Analysis Toolkit (GATK) for secondary analysis.

The service is ISO-certified and compliant with HIPAA regulations, and offers price predictability for your genome sequencing needs.

## Overview

Microsoft Genomics pipeline uses `msgen` tool to submit and observe analyses for all given samples. All submissions 
are scheduled simultaneously to reduce the overall execution time.

Pipeline supports azure storage paths for input and output parameters as well as on-premises
local paths. In case of local paths `CP_TRANSFER_STORAGE` parameter should be specified and an associated 
*data-transfer-service* should be available in Cloud Pipeline.

Outputs of all Microsoft Genomics submissions are grouped by sample names in the specified output directory. 

## Parameters specification

### Required parameters

| Parameter | Description |
| --------- | ----------- |
| **SAMPLE** | Arbitrary sample name |
| **FASTQ1** | Azure storage path to the first fastq file. Supported extensions: `.fa`, `.fa.gz`. |
| **FASTQ2** | Azure storage path to the second fastq file. Supported extensions: `.fa`, `.fa.gz`. |
| **REFERENCE** | One of the predefined reference sequences: `b37m1`, `hg19m1`, `hg38m1`, `hg38m1x`. |
| **PROCESS** | One of the predefined analysis procedures: `gatk4-promo`, `snapgatk`. |
| **OUTPUT** | Azure storage path to store the process outputs. | 

### Optional parameters

| Parameter | Description |
| --------- | ----------- |
| **BSQR** | If specified then *BSQR* analysis step will be performed. |
| **READ_GROUP** | If specified then it will override the default read group created by the process. |
| **EMIT_REF_CONFIDENCE** | One of the predefined output formats: `none`, `gvcf`. If `gvcf` is specified then gVCF files will be generated rather than regular VCF files. | 
| **BGZIP** | If specified then output VCF or gVCF files will be compressed and an index will be created. | 
| **CP_TRANSFER_STORAGE** | If specified then all found input local paths will be transferred to the bucket and all outputs will be transferred from the bucket. |

### System parameters

The following system parameters should be specified for a region where Microsoft Genomics pipeline is launched. 
It can be configured via `launch.env.properties` preference.

| Parameter | Description |
| --------- | ----------- |
| **CP_GENOMICS_REGION** | Microsoft Genomics account region. |
| **CP_GENOMICS_KEY** | Microsoft Genomics account access key. |

### Documentation

Detailed information on the Microsoft Genomics is available at

* https://docs.microsoft.com/ru-ru/azure/genomics/quickstart-run-genomics-workflow-portal
* https://aka.ms/msgatk4 
