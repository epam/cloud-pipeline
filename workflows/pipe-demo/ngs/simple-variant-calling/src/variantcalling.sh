# Inputs:
# =======
# $REFERENCE_GENOME_PATH    - Path to BWA index files, fasta and fasta index
# $PANEL                    - Path to BED for variant calling (chr/start/stop/region_name)
# $FASTQ_R1                 - Path to R1 fastq
# $FASTQ_R2                 - Path to R1 fastq
# $SAMPLE_NAME              - Name of the sample being processed
# Outputs:
# =======
# $RESULT_DIR               - Where to output the results
# Common functions
# ================

function finish_task {
        local _task_name="$1"
        local _task_exit_code="$2"
        if [ $_task_exit_code -eq 0 ]
        then
                pipe_log_success "Task finished successfully" "$_task_name"
        else
                pipe_log_fail "Task failed with exit code $_task_exit_code" "$_task_name"
                exit $_task_exit_code
        fi
}
# Parse provided samples list
# ===========================
IFS=',' read -r -a FASTQ_R1_ARRAY <<< $FASTQ_R1
IFS=$'\n' FASTQ_R1_ARRAY=($(sort <<<"${FASTQ_R1_ARRAY[*]}"))

IFS=',' read -r -a FASTQ_R2_ARRAY <<< $FASTQ_R2
IFS=$'\n' FASTQ_R2_ARRAY=($(sort <<<"${FASTQ_R2_ARRAY[*]}"))

IFS=',' read -r -a SAMPLE_NAME_ARRAY <<< $SAMPLE_NAME
IFS=$'\n' SAMPLE_NAME_ARRAY=($(sort <<<"${SAMPLE_NAME_ARRAY[*]}"))

# Iterate over provided samples
# =============================

for index in "${!SAMPLE_NAME_ARRAY[@]}"
do
        FASTQ_R1_CURRENT="${FASTQ_R1_ARRAY[index]}"
        FASTQ_R2_CURRENT="${FASTQ_R2_ARRAY[index]}"
        SAMPLE_NAME_CURRENT="${SAMPLE_NAME_ARRAY[index]}"

        pipe_log_info "Processing:\n- Sample: $SAMPLE_NAME_CURRENT\n- R1: $FASTQ_R1_CURRENT\n- R2: $FASTQ_R2_CURRENT" "$SAMPLE_NAME_CURRENT"

        # Pipeline steps
        # ==============

        # FASTQC
        FASTQC_OUTPUT=$SAMPLE_NAME_CURRENT/1_fastqc
        mkdir -p $FASTQC_OUTPUT
        pipe_log_info "Started FastQC processing" "$SAMPLE_NAME_CURRENT"
        $FASTQC_BIN -t $(nproc) -o $FASTQC_OUTPUT $FASTQ_R1_CURRENT $FASTQ_R2_CURRENT
        finish_task $SAMPLE_NAME_CURRENT $?

        # BWA
        REFERENCE_GENOME_FASTA_PATH=$(find $REFERENCE_GENOME_PATH -name '*.fa' | sort | head -n 1)
        if [ -z $REFERENCE_GENOME_FASTA_PATH ]
        then
                pipe_log_fail "Reference genome fasta file (.fa) was not found in $REFERENCE_GENOME_PATH" "$SAMPLE_NAME_CURRENT"
                exit 1
        fi

        BWA_OUTPUT=$SAMPLE_NAME_CURRENT/2_bwa
        mkdir -p $BWA_OUTPUT
        pipe_log_info "Started alignment using BWA" "$SAMPLE_NAME_CURRENT"
        $BWA_BIN mem -t $(nproc) -v 1 $REFERENCE_GENOME_FASTA_PATH $FASTQ_R1_CURRENT $FASTQ_R2_CURRENT | \
            $SAMTOOLS_BIN view -b -S - > $BWA_OUTPUT/$SAMPLE_NAME_CURRENT.unsorted.bam
        finish_task $SAMPLE_NAME_CURRENT $?

        # SAMTOOLS SORT AND INDEX
        SORTED_OUTPUT=$SAMPLE_NAME_CURRENT/3_sort
        mkdir -p $SORTED_OUTPUT
        pipe_log_info "Started BAM sort and index" "$SAMPLE_NAME_CURRENT"
        $SAMTOOLS_BIN sort -f $BWA_OUTPUT/$SAMPLE_NAME_CURRENT.unsorted.bam $SORTED_OUTPUT/$SAMPLE_NAME_CURRENT.sorted.bam
        $SAMTOOLS_BIN index $SORTED_OUTPUT/$SAMPLE_NAME_CURRENT.sorted.bam
        finish_task $SAMPLE_NAME_CURRENT $?

        # VARIANT CALLER
        VC_OUTPUT=$SAMPLE_NAME_CURRENT/4_vcf
        mkdir -p $VC_OUTPUT
        pipe_log_info "Started Variant Calling" "$SAMPLE_NAME_CURRENT"
        ${VARDICT_BIN}/build/install/VarDict/bin/VarDict -th $(nproc) -G $REFERENCE_GENOME_FASTA_PATH -f 0.01 -N $SAMPLE_NAME_CURRENT -b $SORTED_OUTPUT/$SAMPLE_NAME_CURRENT.sorted.bam -c 1 -S 2 -E 3 -g 4 $PANEL | \
            ${VARDICT_BIN}/VarDict/teststrandbias.R | \
            ${VARDICT_BIN}/VarDict/var2vcf_valid.pl -N $SAMPLE_NAME_CURRENT -E -f 0.01 | \
            ${BGZIP_BIN} > $VC_OUTPUT/$SAMPLE_NAME_CURRENT.vcf.gz
        ${TABIX_BIN} $VC_OUTPUT/$SAMPLE_NAME_CURRENT.vcf.gz
        finish_task $SAMPLE_NAME_CURRENT $?

        pipe_log_success "Processing finished" "$SAMPLE_NAME_CURRENT"
done
