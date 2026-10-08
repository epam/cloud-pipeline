# [MANUAL] Download with default names into specific folders

Test verifies downloading with per-path-field subfolders created, using the default (unmodified) metadata column values as file names.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the **Metadata** object uploaded at step 11 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 2 | Click on the **Sample [4]** object |  |
| 3 | Set the checkbox in front of the row with ID `4` (save a file name from the value of the **AnotherLocation** column) |  |
| 4 | Click **TRANSFER TO THE CLOUD** |  |
| 5 | Repeat steps 4-8 of the [EPMCMBIBPC-2723](EPMCMBIBPC-2723.md) case |  |
| 6 | In the **Transfer to the Cloud** pop-up, select `AnotherLocation` in the **Select path fields** drop-down list |  |
| 7 | Set the **Create folders for each path field** checkbox |  |
| 8 | Unset the **Update path values** checkbox |  |
| 9 | Click **Start download** |  |
| 10 | Click on the just-launched run with ID `data-transfer-pipeline-<...>` |  |
| 11 | Click the **Parameters** section | The parameters section appears and contains: DESTINATION_DIRECTORY: (full path to the storage created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case); METADATA_COLUMNS: AnotherLocation; CREATE_FOLDERS_FOR_COLUMNS: true; UPDATE_PATH_VALUES: false |
| 12 | Wait until the run with run ID `data-transfer-pipeline-<...>` completes |  |
| 13 | Open the **Library** page, navigate to the folder created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case | A file with the name equal to the value saved at step 3 is not displayed |
| 14 | Open the **AnotherLocation** folder | <li>A file is displayed with the name equal to the value saved at step 3 <li>The file size is more than 0 |
| 15 | Return to the folder created at step 8 of the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 16 | Open the **Location** folder | A file with the name equal to the value saved at step 3 is not displayed |
