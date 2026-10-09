# Validation of set parameters from metadata files

Test verifies binding configuration parameters to Sample-entity fields via the `this.` expression prefix.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1593](EPMCMBIBPC-1593.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the detached configuration edited at step 11 of the [EPMCMBIBPC-1593](EPMCMBIBPC-1593.md) case |  |
| 2 | Click the **Root entity type** combobox |  |
| 3 | Select **Sample** in the list that appears |  |
| 4 | Click the field opposite the **FASTQ_R1** parameter |  |
| 5 | Enter `this.` | A list appears containing: R1_Fastq, R2_Fastq, SampleName |
| 6 | Select `R1_Fastq` in the list that appears |  |
| 7 | Click the field opposite the **FASTQ_R2** parameter |  |
| 8 | Enter `this.` | A list appears containing: R1_Fastq, R2_Fastq, SampleName |
| 9 | Select `R2_Fastq` in the list that appears |  |
| 10 | Click the field opposite the **SAMPLE_NAME** parameter |  |
| 11 | Enter `this.` | A list appears containing: R1_Fastq, R2_Fastq, SampleName |
| 12 | Select `SampleName` in the list that appears |  |
| 13 | Click **Save** |  |
