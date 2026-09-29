# [MANUAL] User own storage validation

Test verifies the UI available to a user for their own storage, with no other permissions in the pipeline library.

**Prerequisites**:
- Same as the prerequisites of the [EPMCMBIBPC-1613](EPMCMBIBPC-1613.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1613](EPMCMBIBPC-1613.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Navigate to the storage from the prerequisites | <li>Download, edit, and delete buttons are located across from file names <li>Edit and delete buttons are located across from folder names <li>The **+ Create** and **Upload** buttons are located to the right, above the file/folder list <li>The **Show attributes**, settings, and **Refresh** buttons are located in the upper-right corner <li>The **Select page** button is located to the left, above the file/folder list <li>The **Show file versions** checkbox is located near the **Select page** button, if the storage has versioning enabled |
| 4 | Hover over the **+ Create** button | A drop-down list appears: Folder, File |
| 5 | Click the settings button (gear icon in the upper-right corner) |  |
| 6 | Open the **Permissions** tab |  |
| 7 | Click the add-user button | The "Select user" pop-up appears |
| 8 | Click **Cancel** |  |
| 9 | Click the add-group button | The "Select group" pop-up appears |
| 10 | Click **Cancel** |  |
| 11 | Close all pop-ups |  |
| 12 | Click on a file name |  |
| 13 | Click the arrow-icon button that appears in the upper-right corner | A pop-up with the file content and **EDIT**, **CLOSE** buttons appears |
