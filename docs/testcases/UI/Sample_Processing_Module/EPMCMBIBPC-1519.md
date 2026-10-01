# Test data preparation

Test creates a storage with fastq files and reference/BWA folders, as shared test data for the sample-processing cases.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v** |  |
| 3 | In the list that appears, select **Storages** -> click **Create new object storage** |  |
| 4 | Specify a valid storage path, click **Create** |  |
| 5 | Open the created storage |  |
| 6 | Hover over **+ Create v** -> click **Folder** |  |
| 7 | Specify the name `fastq` for the folder, click **OK** |  |
| 8 | Open the folder created at step 7 |  |
| 9 | Repeat steps 6-7 with the name `3_replicates_of_NA12878` for the folder |  |
| 10 | Repeat steps 6-7 with the name `11_replicates_of_NA12878` for the folder |  |
| 11 | Open the folder created at step 9 |  |
| 12 | Hover over **+ Create v** -> click **File** |  |
| 13 | Specify the name `NA12878_D710_L001_R1_001.fastq.gz` for the file, click **OK** |  |
| 14 | Repeat steps 12-13 with the following file names: `NA12878_D711_L001_R1_001.fastq.gz`, `NA12878_D712_L001_R1_001.fastq.gz`, `NA12878_D710_L001_R2_001.fastq.gz`, `NA12878_D711_L001_R2_001.fastq.gz`, `NA12878_D712_L001_R2_001.fastq.gz` |  |
| 15 | Navigate to the folder created at step 10 |  |
| 16 | Repeat steps 12-13 with the following file names: `NA12878_D702_L001_R1_001.fastq.gz`, `NA12878_D703_L001_R1_001.fastq.gz`, `NA12878_D704_L001_R1_001.fastq.gz`, `NA12878_D705_L001_R1_001.fastq.gz`, `NA12878_D706_L001_R1_001.fastq.gz`, `NA12878_D707_L001_R1_001.fastq.gz`, `NA12878_D708_L001_R1_001.fastq.gz`, `NA12878_D709_L001_R1_001.fastq.gz`, `NA12878_D710_L001_R1_001.fastq.gz`, `NA12878_D711_L001_R1_001.fastq.gz`, `NA12878_D712_L001_R1_001.fastq.gz`, `NA12878_D702_L001_R2_001.fastq.gz`, `NA12878_D703_L001_R2_001.fastq.gz`, `NA12878_D704_L001_R2_001.fastq.gz`, `NA12878_D705_L001_R2_001.fastq.gz`, `NA12878_D706_L001_R2_001.fastq.gz`, `NA12878_D707_L001_R2_001.fastq.gz`, `NA12878_D708_L001_R2_001.fastq.gz`, `NA12878_D709_L001_R2_001.fastq.gz`, `NA12878_D710_L001_R2_001.fastq.gz`, `NA12878_D711_L001_R2_001.fastq.gz`, `NA12878_D712_L001_R2_001.fastq.gz` |  |
| 17 | Navigate to the root folder of the storage created at step 4 |  |
| 18 | Repeat steps 6-7 with the name `human` for the folder |  |
| 19 | Open the folder created at step 18 |  |
| 20 | Repeat steps 12-13 with the name `gencode.v27.genes.bed` for the file |  |
| 21 | Repeat step 17 |  |
| 22 | Repeat steps 6-7 with the name `reference` for the folder |  |
| 23 | Open the folder created at step 22 |  |
| 24 | Repeat steps 6-7 with the name `bwa` for the folder |  |
