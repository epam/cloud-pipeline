# [MANUAL] User own pipeline validation

Test verifies the UI available to a user for their own pipeline, with no other permissions in the pipeline library.

**Prerequisites**:
- Same as the prerequisites of the [EPMCMBIBPC-1613](EPMCMBIBPC-1613.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1614](EPMCMBIBPC-1614.md) case |  |
| 2 | Login as the user from the prerequisites |  |
| 3 | Navigate to the version of the pipeline from the prerequisites |  |
| 4 | Open the **DOCUMENTS** tab | <li>The **RUN**, settings, and **GIT REPOSITORY** buttons are located in the upper-right corner above the tabs list <li>The **Delete**, **Rename**, **Download** buttons are located opposite every file name <li>The **Upload** button is located to the right, above the file list <li>The **EDIT** button is located to the right, above the readme file content |
| 5 | Open the **CODE** tab | <li>The **+ NEW FILE** and **Upload** buttons are located to the right, above the file/folder list <li>The **+** button is located to the left, above the file/folder list <li>The **Rename** and **Delete** buttons are located opposite every file and folder except `config.json` |
| 6 | Open the **HISTORY** tab | The list of runs is visible, if any |
| 7 | Open the **STORAGE RULES** tab | The **Add new rule** button is visible and clickable |
| 8 | Open the **CONFIGURATION** tab |  |
| 9 | Open the **Exec environment** tab |  |
| 10 | Open the **Advanced** tab | <li>All configuration fields are changeable <li>The **+ ADD** button is present above the configuration form <li>The **Add parameter** button is clickable |
| 11 | Click the settings button (gear icon in the upper-right corner) |  |
| 12 | Open the **Permissions** tab |  |
| 13 | Click the add-user button | The "Select user" pop-up appears |
| 14 | Click **Cancel** |  |
| 15 | Click the add-group button | The "Select group" pop-up appears |
| 16 | Click **Cancel** |  |
