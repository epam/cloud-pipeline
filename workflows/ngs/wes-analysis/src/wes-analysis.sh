#!/usr/bin/env bash

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

COMMON_PIPELINE_TASK="WES_Analysis"

pipe_log_info "Starting '${SAMPLE}' sample analysis" "${COMMON_PIPELINE_TASK}"

READ_FOLDER="$(dirname "${READ1}")"
FASTQ_INPUT_DIR="${READ_FOLDER#${INPUT_DIR}}"
RUN_FOLDER_NAME="${SAMPLE}-${PIPELINE_NAME}-${VERSION}-${RUN_DATE}-${RUN_TIME}-${RUN_ID}"
RUNTIME_FOLDER="${ANALYSIS_DIR}${FASTQ_INPUT_DIR%/PipelineInputData/FASTQ}/${RUN_FOLDER_NAME}"

mkdir -p "${RUNTIME_FOLDER}"

THREADS=${THREADS:-$(nproc)}

function run_task {
    local _TASK_NAME=$1
    local _TASK_FOLDER=$2
    local _TASK_CMD=$3

    pipe_log_info "Starting ${_TASK_NAME} task with command: '${_TASK_CMD}'" "${_TASK_NAME}"
    mkdir -p "${_TASK_FOLDER}"
    pipe_exec "${_TASK_CMD}" "${_TASK_NAME}"
    local _TASK_RESULT=$?
    if [[ ${_TASK_RESULT} -ne 0 ]];
    then
        pipe_log_fail "${_TASK_NAME} task execution failed wih exit code ${_TASK_RESULT}." "${_TASK_NAME}"
        exit ${_TASK_RESULT}
    else
        pipe_log_success "${_TASK_NAME} task finished successfully." "${_TASK_NAME}"
    fi
}

FILE_PREFIX="${SAMPLE}.${RUN_ID}"

  ####################
 ## FastQC Initial ##
####################

FASTQC_INITIAL_TASK="FastQC_Initial"
FASTQC_INITIAL_FOLDER="${RUNTIME_FOLDER}/${FASTQC_INITIAL_TASK}"
FASTQC_INITIAL_CMD="${FASTQC_BIN} -o ${FASTQC_INITIAL_FOLDER} -t ${THREADS} ${READ1} ${READ2}"

run_task "${FASTQC_INITIAL_TASK}" "${FASTQC_INITIAL_FOLDER}" "${FASTQC_INITIAL_CMD}"

  #################
 ## Trimmomatic ##
#################

TRIM_TASK="Trimmomatic"
TRIM_FOLDER="${RUNTIME_FOLDER}/${TRIM_TASK}"
TRIM_FILE_NAME="${TRIM_FOLDER}/${FILE_PREFIX}.${TRIM_TASK}"
TRIMMED_READ1=${TRIM_FILE_NAME}.R1.trimmed.fastq.gz
TRIMMED_READ2=${TRIM_FILE_NAME}.R2.trimmed.fastq.gz
TRIM_CMD="java -jar ${TRIM_BIN} PE -phred33 -threads ${THREADS} ${READ1} ${READ2} \
    ${TRIMMED_READ1} ${TRIM_FILE_NAME}.R1.unpaired.fastq.gz \
    ${TRIMMED_READ2} ${TRIM_FILE_NAME}.R2.unpaired.fastq.gz \
    ILLUMINACLIP:${TRIM_HOME}/adapters/${ADAPTER_FILE}:2:30:10 LEADING:3 TRAILING:3 SLIDINGWINDOW:4:15 MINLEN:36"

run_task "${TRIM_TASK}" "${TRIM_FOLDER}" "${TRIM_CMD}"

  ###################
 ## FastQC Trimmed##
###################

FASTQC_TRIM_TASK="FastQC_Trimmed"
FASTQC_TRIM_FOLDER="${RUNTIME_FOLDER}/${FASTQC_TRIM_TASK}"
FASTQC_TRIM_CMD="${FASTQC_BIN} -o ${FASTQC_TRIM_FOLDER} -t ${THREADS} ${TRIMMED_READ1} ${TRIMMED_READ2}"

run_task "${FASTQC_TRIM_TASK}" "${FASTQC_TRIM_FOLDER}" "${FASTQC_TRIM_CMD}"

  ###########
 ## Align ##
###########

ALIGN_TASK="Alignment"
ALIGN_FOLDER="${RUNTIME_FOLDER}/${ALIGN_TASK}"

REFERENCE_FASTA=${REFERENCE_FOLDER}/genome/${REFERENCE_NAME}.fa
BAM_ALIGNED="${ALIGN_FOLDER}/${FILE_PREFIX}.${ALIGN_TASK}.bam"

ALIGN_CMD="${BWA_BIN} mem -t ${THREADS} -v 2 ${REFERENCE_FASTA} \
    ${TRIMMED_READ1} ${TRIMMED_READ2} | ${SAMTOOLS_BIN} view -bS -o ${BAM_ALIGNED} -"

run_task "${ALIGN_TASK}" "${ALIGN_FOLDER}" "${ALIGN_CMD}"

  ############################
 ## Fix and Set Mate Tags ##
############################
FIX_MATES_TASK="FixMates"
FIX_MATES_FOLDER="${RUNTIME_FOLDER}/${FIX_MATES_TASK}"

BAM_FIXED="${FIX_MATES_FOLDER}/${FILE_PREFIX}.${FIX_MATES_TASK}.bam"
FIX_MATES_CMD="java -jar ${PICARD_BIN} FixMateInformation I=${BAM_ALIGNED} O=${BAM_FIXED} SORT_ORDER=coordinate"

run_task "${FIX_MATES_TASK}" "${FIX_MATES_FOLDER}" "${FIX_MATES_CMD}"


  #####################
 ## Index Fixed BAM ##
#####################
INDEX_BAM_TASK="IndexBAM"
INDEX_BAM_FOLDER="${FIX_MATES_FOLDER}"
INDEX_BAM_CMD="${SAMTOOLS_BIN} index ${BAM_FIXED} ${BAM_FIXED}.bai"
run_task "${INDEX_BAM_TASK}" "${INDEX_BAM_FOLDER}" "${INDEX_BAM_CMD}"

  #######################
 ## BAM Aligned stats ##
#######################

STATS_ALIGN_TASK="BAMStats_Alignment"
STATS_ALIGN_FOLDER="${RUNTIME_FOLDER}/${STATS_ALIGN_TASK}"
STATS_ALIGN_CMD="${SAMTOOLS_BIN} flagstat ${BAM_FIXED} > ${STATS_ALIGN_FOLDER}/flagstat.${FILE_PREFIX}.${STATS_ALIGN_TASK}.txt"

run_task "${STATS_ALIGN_TASK}" "${STATS_ALIGN_FOLDER}" "${STATS_ALIGN_CMD}"


  #######################
 ## InsertSizeMetrics ##
#######################
INS_SIZE_TASK="InsertSizeMetrics"
INS_SIZE_FOLDER="${RUNTIME_FOLDER}/${INS_SIZE_TASK}"
INS_SIZE_CMD="java -jar ${PICARD_BIN} CollectInsertSizeMetrics I=${BAM_FIXED} \
    O=${INS_SIZE_FOLDER}/metrics.${FILE_PREFIX}.${INS_SIZE_TASK}.txt \
    H=${INS_SIZE_FOLDER}/histogram.${FILE_PREFIX}.${INS_SIZE_TASK}.pdf"

run_task "${INS_SIZE_TASK}" "${INS_SIZE_FOLDER}" "${INS_SIZE_CMD}"

  #############################
 ## AlignmentSummaryMetrics ##
#############################
ALIGN_SUMMARY_TASK="AlignmentSummaryMetrics"
ALIGN_SUMMARY_FOLDER="${RUNTIME_FOLDER}/${ALIGN_SUMMARY_TASK}"
ALIGN_SUMMARY_CMD="java -jar ${PICARD_BIN} CollectAlignmentSummaryMetrics I=${BAM_FIXED} \
    O=${ALIGN_SUMMARY_FOLDER}/metrics.${FILE_PREFIX}.${ALIGN_SUMMARY_TASK}.txt \
    R=${REFERENCE_FASTA}"

run_task "${ALIGN_SUMMARY_TASK}" "${ALIGN_SUMMARY_FOLDER}" "${ALIGN_SUMMARY_CMD}"

  ##############
 ## Coverage ##
##############
COVERAGE_TASK="CoverageMetrics"
COVERAGE_FOLDER="${RUNTIME_FOLDER}/${COVERAGE_TASK}"
COVERAGE_CMD="samtools depth ${BAM_FIXED} > ${COVERAGE_FOLDER}/${FILE_PREFIX}.${COVERAGE_TASK}.txt"
run_task "${COVERAGE_TASK}" "${COVERAGE_FOLDER}" "${COVERAGE_CMD}"

  ##################
 ## Remove panel ##
##################

REMOVE_PANEL_TASK="RemoveOffPanel"
REMOVE_PANEL_FOLDER="${RUNTIME_FOLDER}/${REMOVE_PANEL_TASK}"

PANEL_FILE="${REFERENCE_FOLDER}/panels/${PANEL_NAME}"
INTERVAL_LIST_FILE="${REMOVE_PANEL_FOLDER}/panel.list"
SEQ_DICT_FILE="${REFERENCE_FASTA}.dict"
BAM_PANEL="${REMOVE_PANEL_FOLDER}/${FILE_PREFIX}.${REMOVE_PANEL_TASK}.bam"

REMOVE_PANEL_CMD="java -jar ${PICARD_BIN} BedToIntervalList I=${PANEL_FILE} O=${INTERVAL_LIST_FILE} SD=${SEQ_DICT_FILE} \
    && java -jar ${PICARD_BIN} FilterSamReads FILTER=includePairedIntervals I=${BAM_FIXED} \
    INTERVAL_LIST=${INTERVAL_LIST_FILE} WRITE_READS_FILES=false O=${BAM_PANEL}"

run_task "${REMOVE_PANEL_TASK}" "${REMOVE_PANEL_FOLDER}" "${REMOVE_PANEL_CMD}"

  ############################
 ## Sort and Index Panel ##
############################
SORT_PANEL_TASK="SortPanel"
SORT_PANEL_FOLDER="${RUNTIME_FOLDER}/${SORT_PANEL_TASK}"
BAM_PANEL_SORTED="${SORT_PANEL_FOLDER}/${FILE_PREFIX}.${SORT_PANEL_TASK}.bam"
SORT_PANEL_CMD="${SAMTOOLS_BIN} sort -@ ${THREADS} -f ${BAM_PANEL} ${BAM_PANEL_SORTED} && \
    ${SAMTOOLS_BIN} index ${BAM_PANEL_SORTED} ${BAM_PANEL_SORTED}.bai"

run_task "${SORT_PANEL_TASK}" "${SORT_PANEL_FOLDER}" "${SORT_PANEL_CMD}"


  #######################
 ## BAM Panel stats ##
#######################
STATS_PANEL_TASK="BAMStats_Panel"
STATS_PANEL_FOLDER="${RUNTIME_FOLDER}/${STATS_PANEL_TASK}"
STATS_PANEL_CMD="${SAMTOOLS_BIN} flagstat ${BAM_PANEL_SORTED} > ${STATS_PANEL_FOLDER}/flagstat.${FILE_PREFIX}.${STATS_PANEL_TASK}.txt"

run_task "${STATS_PANEL_TASK}" "${STATS_PANEL_FOLDER}" "${STATS_PANEL_CMD}"

  #############
 ## Realign ##
#############
REALIGN_TASK="Realign"
REALIGN_FOLDER="${RUNTIME_FOLDER}/${REALIGN_TASK}"
BAM_REALIGNED="${REALIGN_FOLDER}/${FILE_PREFIX}.${REALIGN_TASK}.bam"
REALIGN_CMD="java -jar ${ABRA2_BIN} --ref ${REFERENCE_FASTA} --threads ${THREADS} --in ${BAM_PANEL_SORTED} --out ${BAM_REALIGNED}"

run_task "${REALIGN_TASK}" "${REALIGN_FOLDER}" "${REALIGN_CMD}"

  ##########################
 ## Fix Mates After ABRA ##
##########################
FIX_REALIGNED_TASK="FixMatesRealigned"
FIX_REALIGNED_FOLDER="${RUNTIME_FOLDER}/${FIX_REALIGNED_TASK}"

BAM_FIXED_REALIGNED="${FIX_REALIGNED_FOLDER}/${FILE_PREFIX}.${FIX_REALIGNED_TASK}.bam"
FIX_REALIGNED_CMD="java -jar ${PICARD_BIN} FixMateInformation I=${BAM_REALIGNED} O=${BAM_FIXED_REALIGNED} SORT_ORDER=coordinate"

run_task "${FIX_REALIGNED_TASK}" "${FIX_REALIGNED_FOLDER}" "${FIX_REALIGNED_CMD}"

  #########################
 ## Index Realigned BAM ##
#########################
INDEX_REALIGNED_TASK="IndexBAMRealigned"
INDEX_REALIGNED_FOLDER="${FIX_REALIGNED_FOLDER}"
INDEX_REALIGNED_CMD="${SAMTOOLS_BIN} index ${BAM_FIXED_REALIGNED} ${BAM_FIXED_REALIGNED}.bai"
run_task "${INDEX_REALIGNED_TASK}" "${INDEX_REALIGNED_FOLDER}" "${INDEX_REALIGNED_CMD}"

  #####################
 ## Mark Duplicates ##
#####################
MARK_DUP_TASK="MarkDuplicates"
MARK_DUP_FOLDER="${RUNTIME_FOLDER}/${MARK_DUP_TASK}"

BAM_FINAL="${MARK_DUP_FOLDER}/${FILE_PREFIX}.final.bam"
MARK_DUP_CMD="java -jar ${PICARD_BIN} MarkDuplicates I=${BAM_FIXED_REALIGNED} O=${BAM_FINAL} REMOVE_DUPLICATES=False \
    METRICS_FILE=${MARK_DUP_FOLDER}/markduplicates.${FILE_PREFIX}.${MARK_DUP_TASK}.txt"
run_task "${MARK_DUP_TASK}" "${MARK_DUP_FOLDER}" "${MARK_DUP_CMD}"


  #######################
 ## Index Final BAM ##
#######################
INDEX_FINAL_TASK="IndexFinalBAM"
INDEX_FINAL_FOLDER="${RUNTIME_FOLDER}/${INDEX_FINAL_TASK}"
INDEX_FINAL_CMD="${SAMTOOLS_BIN} index ${BAM_FINAL} ${BAM_FINAL}.bai"
run_task "${INDEX_FINAL_TASK}" "${INDEX_FINAL_FOLDER}" "${INDEX_FINAL_CMD}"

  #####################
 ## BAM Final stats ##
#####################
STATS_FINAL_TASK="BAMStats_Final"
STATS_FINAL_FOLDER="${RUNTIME_FOLDER}/${STATS_FINAL_TASK}"
STATS_FINAL_CMD="${SAMTOOLS_BIN} flagstat ${BAM_FINAL} > ${STATS_FINAL_FOLDER}/flagstat.${FILE_PREFIX}.${STATS_FINAL_TASK}.txt"

run_task "${STATS_FINAL_TASK}" "${STATS_FINAL_FOLDER}" "${STATS_FINAL_CMD}"

  ###################
 ## VariantCaller ##
###################
VARIANT_CALLING_TASK="VariantCalling"
VARIANT_CALLING_FOLDER="${RUNTIME_FOLDER}/${VARIANT_CALLING_TASK}"
VCF_INITIAL="${VARIANT_CALLING_FOLDER}/${FILE_PREFIX}.${VARIANT_CALLING_TASK}.vcf"

VARIANT_CALLING_CMD="${VARDICT_BIN}/build/install/VarDict/bin/VarDict -G ${REFERENCE_FASTA} -f ${AF} -N ${SAMPLE} -b ${BAM_FINAL} \
    -z -c 1 -S 2 -E 3 -g 4 ${PANEL_FILE} -th ${THREADS} | ${VARDICT_BIN}/VarDict/teststrandbias.R | \
    ${VARDICT_BIN}/VarDict/var2vcf_valid.pl -N ${SAMPLE} -E -f ${AF} > ${VCF_INITIAL}"

run_task "${VARIANT_CALLING_TASK}" "${VARIANT_CALLING_FOLDER}" "${VARIANT_CALLING_CMD}"

  #################
 ##  CNV Caller ##
#################
CNV_CALLING_TASK="CNVCalling"
CNV_CALLING_FOLDER="${RUNTIME_FOLDER}/${CNV_CALLING_TASK}"
SEQ2C_INPUT_FILE="${RUNTIME_FOLDER}/seq2c-input.txt"
echo -e "${SAMPLE}"'\t'"${BAM_FINAL}" > ${SEQ2C_INPUT_FILE}
CNV_CALLING_CMD="cd ${CNV_CALLING_FOLDER} && ${SEQ2C_BIN} ${SEQ2C_INPUT_FILE} ${PANEL_FILE} ${CNV_CALLING_FOLDER}/seq2c-coverage.txt -i ${THREADS} > \
    ${CNV_CALLING_FOLDER}/${FILE_PREFIX}.${CNV_CALLING_TASK}.txt && cd ${ANALYSIS_DIR}"

run_task "${CNV_CALLING_TASK}" "${CNV_CALLING_FOLDER}" "${CNV_CALLING_CMD}"
rm "${SEQ2C_INPUT_FILE}"

  ##############
 ## Sort VCF ##
##############
SORT_VCF_TASK="SortVCF"
SORT_VCF_FOLDER="${RUNTIME_FOLDER}/${SORT_VCF_TASK}"
VCF_SORTED="${SORT_VCF_FOLDER}/${FILE_PREFIX}.${SORT_VCF_TASK}.vcf"
SORT_VCF_CMD="vcf-sort ${VCF_INITIAL} > ${VCF_SORTED}"

run_task "${SORT_VCF_TASK}" "${SORT_VCF_FOLDER}" "${SORT_VCF_CMD}"

  ##################
 ## Annotate VCF ##
##################
ANNOTATE_VCF_TASK="VariantAnnotation"
ANNOTATE_VCF_FOLDER="${RUNTIME_FOLDER}/${ANNOTATE_VCF_TASK}"
VCF_ANNOTATED="${ANNOTATE_VCF_FOLDER}/${FILE_PREFIX}.${ANNOTATE_VCF_TASK}.vcf"
ANNOTATE_VCF_CMD="cd ${ANNOTATE_VCF_FOLDER} && java -jar ${SNPEFF_BIN} ${SNPEFF_DB_NAME} ${VCF_SORTED} -v -noLog -nodownload \
    -dataDir ${REFERENCE_FOLDER}/annotation/snpeff > ${VCF_ANNOTATED} && cd ${ANALYSIS_DIR}"

run_task "${ANNOTATE_VCF_TASK}" "${ANNOTATE_VCF_FOLDER}" "${ANNOTATE_VCF_CMD}"

  ##################
 ## Compress VCF ##
##################
COMPRESS_VCF_TASK="CompressVCF"
COMPRESS_VCF_FOLDER="${RUNTIME_FOLDER}/${COMPRESS_VCF_TASK}"
VCF_FINAL="${COMPRESS_VCF_FOLDER}/${FILE_PREFIX}.final.vcf.gz"
COMPRESS_VCF_CMD="${BGZIP_BIN} -c ${VCF_ANNOTATED} > ${VCF_FINAL} && ${TABIX_BIN} ${VCF_FINAL}"

run_task "${COMPRESS_VCF_TASK}" "${COMPRESS_VCF_FOLDER}" "${COMPRESS_VCF_CMD}"


  #########################
 ## Variants QC metrics ##
#########################
VARIANTS_METRICS_TASK="CollectVariantsMetrics"
VARIANTS_METRICS_FOLDER="${RUNTIME_FOLDER}/${VARIANTS_METRICS_TASK}"
VARIANTS_METRICS_CMD="${CP_PYTHON2_PATH} ${SCRIPTS_DIR}/src/collect_variant_metrics.py --task ${VARIANTS_METRICS_TASK} \
    --vcf ${VCF_ANNOTATED} --output-file ${VARIANTS_METRICS_FOLDER}/${FILE_PREFIX}.${VARIANTS_METRICS_TASK}.txt"
run_task "${VARIANTS_METRICS_TASK}" "${VARIANTS_METRICS_FOLDER}" "${VARIANTS_METRICS_CMD}"

  #######################
 ## Sample QC metrics ##
#######################
SAMPLE_METRICS_TASK="CollectSampleMetrics"
SAMPLE_METRICS_FOLDER="${RUNTIME_FOLDER}/${SAMPLE_METRICS_TASK}"
SAMPLE_METRICS_CMD="${CP_PYTHON2_PATH} ${SCRIPTS_DIR}/src/collect_sample_metrics.py --task ${SAMPLE_METRICS_TASK} --folder ${RUNTIME_FOLDER} \
    --file-suffix ${FILE_PREFIX} --sample ${SAMPLE} --output-file ${SAMPLE_METRICS_FOLDER}/${FILE_PREFIX}.${SAMPLE_METRICS_TASK}.txt"
run_task "${SAMPLE_METRICS_TASK}" "${SAMPLE_METRICS_FOLDER}" "${SAMPLE_METRICS_CMD}"

pipe_log_success "Successfully finished '${SAMPLE}' sample analysis" "${COMMON_PIPELINE_TASK}"

