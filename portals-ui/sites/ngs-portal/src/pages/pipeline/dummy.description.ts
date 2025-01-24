export const dummyDescription = `# NGS Pipeline: A Comprehensive Overview

Next-Generation Sequencing (NGS) pipelines are a series of automated computational processes used to analyze sequencing data. Below is a description of a typical NGS pipeline, highlighting each step, the tools used, and the corresponding output files.

---

## **1. Raw Data Acquisition**
### **Description**
This step involves the acquisition of raw sequencing reads from the sequencing platform, typically provided in **FASTQ** format.

### **Input**: Raw sequencing data files (e.g., FASTQ)
### **Output**: Verified and organized FASTQ files
### **Tools**: N/A (Sequencing instruments generate this data)

---

## **2. Quality Control (QC)**
### **Description**
Assess the quality of the raw sequencing reads and remove low-quality bases and adapter contamination.

### **Input**: FASTQ files
### **Output**: Filtered FASTQ files, quality metrics report
### **Tools**:
- **FastQC**: Provides a visual summary of sequencing quality.
- **Trimmomatic** or **Cutadapt**: Performs adapter trimming and low-quality base removal.

---

## **3. Alignment to Reference Genome**
### **Description**
Map the high-quality reads to a reference genome to determine the genomic location of each read.

### **Input**: Filtered FASTQ files, Reference genome (e.g., FASTA format)
### **Output**: Aligned read files in **SAM/BAM** format
### **Tools**:
- **BWA** (Burrows-Wheeler Aligner)
- **HISAT2**
- **Bowtie2**

---

## **4. Post-Alignment Processing**
### **Description**
Refine the aligned data by sorting, marking duplicates, and recalibrating base quality scores.

### **Input**: SAM/BAM files
### **Output**: Processed BAM files, Index files
### **Tools**:
- **Samtools**: Sorting and indexing
- **Picard**: Marking duplicates
- **GATK** (Genome Analysis Toolkit): Base quality score recalibration (BQSR)

---

## **5. Variant Calling**
### **Description**
Identify genomic variants (e.g., SNPs, Indels) from the aligned reads.

### **Input**: Processed BAM files, Reference genome
### **Output**: Variant files in **VCF** format
### **Tools**:
- **GATK HaplotypeCaller**
- **FreeBayes**
- **Bcftools**

---

## **6. Variant Annotation**
### **Description**
Annotate the identified variants with additional information such as functional effects, population frequencies, and disease associations.

### **Input**: VCF files
### **Output**: Annotated VCF or tabular files
### **Tools**:
- **ANNOVAR**
- **VEP** (Variant Effect Predictor)
- **SnpEff**

---

## **7. Visualization and Reporting**
### **Description**
Generate visualizations and summaries for downstream interpretation and decision-making.

### **Input**: BAM files, VCF files, and annotations
### **Output**: Plots, summary tables, and reports
### **Tools**:
- **IGV** (Integrative Genomics Viewer): Visualization of aligned reads and variants.
- **R**/**Python** libraries: Custom statistical analysis and visualization.
- **MultiQC**: Aggregates QC metrics into a single report.

---

## **Summary Workflow**

\`\`\`mermaid
graph TD
    A[Raw FASTQ Files] --> B[Quality Control]
    B --> C[Alignment to Reference Genome]
    C --> D[Post-Alignment Processing]
    D --> E[Variant Calling]
    E --> F[Variant Annotation]
    F --> G[Visualization and Reporting]
`;
