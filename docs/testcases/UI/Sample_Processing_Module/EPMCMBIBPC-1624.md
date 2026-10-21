# Validation of set parameters from metadata files (SampleSet)

Test verifies binding configuration parameters to SampleSet-linked Sample fields via the `this.Samples.` expression path.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1588](EPMCMBIBPC-1588.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the detached configuration created at step 5 of the [EPMCMBIBPC-1588](EPMCMBIBPC-1588.md) case |  |
| 2 | Perform the [EPMCMBIBPC-1593](EPMCMBIBPC-1593.md) and [EPMCMBIBPC-1623](EPMCMBIBPC-1623.md) cases |  |
| 3 | Click the field opposite the **FASTQ_R1** parameter |  |
| 4 | Enter `this.` | A drop-down list appears, containing the **Name**, **Samples** items |
| 5 | Select `Samples` in the list that appears, enter `.` | A drop-down list appears, containing the **R1_Fastq**, **R2_Fastq**, **SampleName** items |
| 6 | Select `R1_Fastq` in the list that appears |  |
| 7 | Click the field opposite the **FASTQ_R2** parameter |  |
| 8 | Enter `this.` | A drop-down list appears, containing the **Name**, **Samples** items |
| 9 | Select `Samples` in the list that appears, enter `.` | A drop-down list appears, containing the **R1_Fastq**, **R2_Fastq**, **SampleName** items |
| 10 | Select `R2_Fastq` in the list that appears |  |
| 11 | Click the field opposite the **SAMPLE_NAME** parameter |  |
| 12 | Enter `this.` | A drop-down list appears, containing the **Name**, **Samples** items |
| 13 | Select `Samples` in the list that appears, enter `.` | A drop-down list appears, containing the **R1_Fastq**, **R2_Fastq**, **SampleName** items |
| 14 | Select `SampleName` in the list that appears |  |
| 15 | Click **Save** |  |
