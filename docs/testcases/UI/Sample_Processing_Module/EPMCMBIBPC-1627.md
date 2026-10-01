# Validation of pipeline parameters (SampleSet)

Test verifies the resolved parameter values for a SampleSet-launched pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1626](EPMCMBIBPC-1626.md) case |  |
| 2 | Click on the launched pipeline |  |
| 3 | Click the **Parameters** collapsed header | The following parameters are displayed: <li>PANEL - the path to the file specified at step 5 of the EPMCMBIBPC-1520 case (hyperlink) <li>FASTQ_R2 - the file list created at steps 13, 14 of the EPMCMBIBPC-1519 case with the postfix "R2_001.fastq.gz" (hyperlinks) <li>FASTQ_R1 - the file list created at steps 13, 14 of the EPMCMBIBPC-1519 case with the postfix "R1_001.fastq.gz" (hyperlinks) <li>REFERENCE_GENOME_PATH - the path to the file specified at step 7 of the EPMCMBIBPC-1520 case (hyperlink) <li>RESULT_DIR - the path to the file specified at step 8 of the EPMCMBIBPC-1520 case (hyperlink), where `${RUN_ID}` is replaced by the pipeline ID <li>SAMPLE_NAME - the following list is displayed: `NA12878_D710_3`, `NA12878_D711_3`, `NA12878_D712_3` <li>CP_CAP_NFS - true |
