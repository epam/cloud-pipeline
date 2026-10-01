# [MANUAL] Download with default names

Test verifies downloading with the default (unmodified) metadata column values used as file names, and no per-path-field subfolders.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the **Metadata** object uploaded at step 11 of the EPMCMBIBPC-2722 case |  |
| 2 | Click on the **Sample [4]** object |  |
| 3 | Set the checkbox in front of the row with ID `3` (save a file name from the value of the **Location** column) |  |
| 4 | Click **TRANSFER TO THE CLOUD** |  |
| 5 | Repeat steps 4-8 of the [EPMCMBIBPC-2723](EPMCMBIBPC-2723.md) case |  |
| 6 | In the **Transfer to the Cloud** pop-up, select `Location` in the **Select path fields** drop-down list |  |
| 7 | Unset the **Update path values** checkbox |  |
| 8 | Click **Start download** |  |
| 9 | Click on the just-launched run with ID `data-transfer-pipeline-<...>` |  |
| 10 | Click the **Parameters** section | The parameters section appears and contains: DESTINATION_DIRECTORY: (full path to the storage created at step 8 of the EPMCMBIBPC-2722 case); METADATA_COLUMNS: Location; FILE_NAME_FORMAT_COLUMN: Name_short; CREATE_FOLDERS_FOR_COLUMNS: false; UPDATE_PATH_VALUES: false |
| 11 | Wait until the run with run ID `data-transfer-pipeline-<...>` completes |  |
| 12 | Open the **Library** page, navigate to the folder created at step 8 of the EPMCMBIBPC-2722 case | <li>A file is displayed with the name equal to the value saved at step 3 <li>The file size is more than 0 |
