# [MANUAL] Download with specifying names into several folders

Test verifies downloading metadata-referenced files into per-path-field subfolders, with custom file naming and no path-value updates.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2722](EPMCMBIBPC-2722.md) case |  |
| 2 | Set the checkbox in front of the row with ID `1` (save the value from the **Name** column) |  |
| 3 | Click **TRANSFER TO THE CLOUD** |  |
| 4 | In the **Transfer to the Cloud** pop-up, click **Browse** near the **Destination** field |  |
| 5 | In the pop-up that appears, click on the folder created at step 3 of the EPMCMBIBPC-2722 case |  |
| 6 | Click on the storage created at step 6 of the EPMCMBIBPC-2722 case |  |
| 7 | Set the checkbox in front of the folder created at step 8 of the EPMCMBIBPC-2722 case |  |
| 8 | Click **OK** |  |
| 9 | In the **Transfer to the Cloud** pop-up, select both items in the **Select path fields** drop-down list (`AnotherLocation`, `Location`) |  |
| 10 | Select `Name` in the **Select name field** drop-down list |  |
| 11 | Set the **Create folders for each path field** checkbox |  |
| 12 | Unset the **Update path values** checkbox |  |
| 13 | Click **Start download** | <li>The **Runs** page opens <li>On the **ACTIVE RUNS** tab, a run with run ID `data-transfer-pipeline-<...>` appears |
| 14 | Click on the just-launched run with ID `data-transfer-pipeline-<...>` |  |
| 15 | Click the **Parameters** section | The parameters section appears and contains: DESTINATION_DIRECTORY: (full path to the storage created at step 8 of the EPMCMBIBPC-2722 case); METADATA_COLUMNS: AnotherLocation Location; FILE_NAME_FORMAT_COLUMN: Name; CREATE_FOLDERS_FOR_COLUMNS: true; UPDATE_PATH_VALUES: false |
| 16 | Wait until the run with run ID `data-transfer-pipeline-<...>` completes |  |
| 17 | Open the **Library** page, navigate to the folder created at step 8 of the EPMCMBIBPC-2722 case | 2 subfolders with the names "AnotherLocation" and "Location" appear in the folder created at step 8 of the EPMCMBIBPC-2722 case |
| 18 | Open the **AnotherLocation** folder | <li>A file is displayed with the name equal to the value saved at step 2 <li>The file size is more than 0 |
| 19 | Return to the folder created at step 8 of the EPMCMBIBPC-2722 case |  |
| 20 | Open the **Location** folder | <li>A file is displayed with the name equal to the value saved at step 2 <li>The file size is more than 0 |
