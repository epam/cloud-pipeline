# [MANUAL] Download with updating path values

Test verifies that downloading with "Update path values" enabled turns the metadata column value into a hyperlink to the downloaded file.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the **Metadata** object uploaded at step 11 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 2 | Click on the **Sample [4]** object |  |
| 3 | Set the checkbox in front of the row with ID `2` (save the value from the **Name_short** column) |  |
| 4 | Click **TRANSFER TO THE CLOUD** |  |
| 5 | Repeat steps 4-8 of the [EPMCMBIBPC-2723](EPMCMBIBPC-2723.md) case |  |
| 6 | In the **Transfer to the Cloud** pop-up, select `AnotherLocation` in the **Select path fields** drop-down list |  |
| 7 | Select `Name_short` in the **Select name field** drop-down list |  |
| 8 | Set the **Create folders for each path field** checkbox |  |
| 9 | Check that the **Update path values** checkbox is set |  |
| 10 | Click **Start download** |  |
| 11 | Click on the just-launched run with ID `data-transfer-pipeline-<...>` |  |
| 12 | Click the **Parameters** section | The parameters section appears and contains: DESTINATION_DIRECTORY: (full path to the storage created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case); METADATA_COLUMNS: AnotherLocation; FILE_NAME_FORMAT_COLUMN: Name_short; CREATE_FOLDERS_FOR_COLUMNS: true; UPDATE_PATH_VALUES: true |
| 13 | Wait until the run with run ID `data-transfer-pipeline-<...>` completes |  |
| 14 | Open the **Library** page, navigate to the folder created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 15 | Open the **AnotherLocation** folder | <li>A file is displayed with the name equal to the value saved at step 3 <li>The file size is more than 0 |
| 16 | Return to the folder created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 17 | Open the **Location** folder | A file with the name equal to the value saved at step 3 is not displayed |
| 18 | Repeat steps 1-2 | In the row with ID `2`: <li>the value in the **AnotherLocation** column is displayed as a hyperlink <li>the value in the **Location** column is not displayed as a hyperlink <li>the hyperlink text in the **AnotherLocation** column is equal to the value saved at step 3 |
| 19 | Click the hyperlink in the **AnotherLocation** column of the row with ID `2` | <li>The **AnotherLocation** folder (in the folder created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case) opens <li>A file with the name equal to the value saved at step 3 is displayed |
