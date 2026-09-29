# Validation of pipeline parameters

Test verifies the resolved parameter values for a per-sample launched pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1599](EPMCMBIBPC-1599.md) case |  |
| 2 | Click on any launched pipeline |  |
| 3 | Click the **Parameters** collapsed header | The following parameters are displayed: <li>PANEL - the path to the file specified at step 5 of the EPMCMBIBPC-1520 case (hyperlink) <li>FASTQ_R2 - the path to the file from the **R2_Fastq** column of the row selected at step 2 of the EPMCMBIBPC-1599 case (hyperlink) <li>FASTQ_R1 - the path to the file from the **R1_Fastq** column of the row selected at step 2 of the EPMCMBIBPC-1599 case (hyperlink) <li>REFERENCE_GENOME_PATH - the path to the file specified at step 7 of the EPMCMBIBPC-1520 case (hyperlink) <li>RESULT_DIR - the path to the file specified at step 8 of the EPMCMBIBPC-1520 case (hyperlink), where `${RUN_ID}` is replaced by the pipeline ID <li>SAMPLE_NAME - the value from the **ID** column of the row selected at step 2 of the EPMCMBIBPC-1599 case <li>CP_CAP_NFS - true |
