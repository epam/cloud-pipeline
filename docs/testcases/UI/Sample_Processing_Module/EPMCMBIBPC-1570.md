# Metadata edit and prepare

Test verifies editing sample metadata to point R1_Fastq/R2_Fastq at real storage paths, turning them into hyperlinks.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1412](EPMCMBIBPC-1412.md) case |  |
| 2 | Click on the **Metadata** object in the library tree |  |
| 3 | Click on the **Sample** object |  |
| 4 | Click on the first row of the table that appears |  |
| 5 | In the **Attributes** panel that appears, click the **Value** field of the `R1_Fastq` key |  |
| 6 | In the value, change the path to the storage to the path of the storage created at step 4 of the [EPMCMBIBPC-1519](EPMCMBIBPC-1519.md) case |  |
| 7 | Click on an empty area | The record in the first row of the **R1_Fastq** column becomes a hyperlink |
| 8 | In the **Attributes** panel, click the **Value** field of the `R2_Fastq` key |  |
| 9 | In the value, change the path to the storage to the path of the storage created at step 4 of the [EPMCMBIBPC-1519](EPMCMBIBPC-1519.md) case |  |
| 10 | Click on an empty area | The record in the first row of the **R2_Fastq** column becomes a hyperlink |
| 11 | Repeat steps 4-10 for all table rows | The records in all rows of the **R1_Fastq** and **R2_Fastq** columns become hyperlinks |
