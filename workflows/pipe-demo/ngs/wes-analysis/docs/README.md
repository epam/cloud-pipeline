# Whole Exome Sequencing Pipeline

## Overview
`Whole Exome Sequencing Pipeline` (`WES Pipeline`) is an example of analytical pipeline for NGS data processing. It operates on the sample-level and performs BAM/VCF/Statistics files generation from the FASTQ files, provided by the upstream `Demultiplexing` step. The main goal of `WES Pipeline` is to identify and annotate variants and collect metrics for a sequenced sample.

## WES Pipeline Steps
`WES Pipeline` includes five major stages each consisting of a number of smaller steps, including running some tools or custom scripts

### 1. QC and Pre-alignment
- Validation of the input parameters and FASTQ files occurs. (`FASTQC`)
- FASTQ metrics are collected (e.g. Total number of reads, Number of reads after trimming, etc.) (`FASTQC`)
- Adapters are trimmed (`Trimmomatic`)

### 2. Alignment
- FASTQ files are aligned to a reference genome (`BWA`)
- Alignment metrics are collected (e.g. Number of aligned reads, Insert sizes, etc.) (`samtools`, `Picard Tools`)
- All the "Off Panel" reads are removed from the BAM file with certain metrics collected as well (e.g. Number of reads Off-Panel, etc.).  (`Picard Tools`)

### 3. Post-alignment
- BAM file is realigned to improve further variant calling  (`Abra2`)
- Duplicated reads in the BAM file are marked/removed and corresponding metrics are collected (e.g. Number of duplicates removed, etc.)  (`Picard Tools`)

### 4.  Variant Calling
- SNV/InDel/CNV callers are run to generate a "raw" VCF and CNV files from the final BAM. (`Vardict`, `Seq2c`)

### 5. Variant annotation and filtering
- "Raw" VCF file is annotated using available databases (`SnpEff`)
- Variants metrics are collected from annotated VCF and CNV files (`custom script`)
- Sample-level statistics file is generated, including metrics collected during all stages of the same processing (`custom script`)

## WES Pipeline Parameters
- `SAMPLE` [required] - name of analysed sample
- `READ1` [required] - path to FASTQ file with R1 reads
- `READ2` [required] - path to FASTQ file with R2 reads
- `REFERENCE_FOLDER` [required] - path to folder with reference files: FASTA, FAI, DICT and related annotation files and panels
- `REFERENCE_NAME` [required] - name of reference FASTA file, must be present in  `REFERENCE_FOLDER`
- `PANEL_NAME` [required] - name of panel BED file, must be present in  `REFERENCE_FOLDER`
- `SNPEFF_DB_NAME` [required] - name of the folder with SnpEff annotation db, must be present in  `REFERENCE_FOLDER`
- `AF` [required] - minimal allele frequency for Variant caller
- `ADAPTER_FILE` [required] - name of the adapter file, must be supported by `Trimmomatic` tool
- `OUTPUT_FOLDER` [required] - path to output results of pipeline run
