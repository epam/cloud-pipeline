# [MANUAL] Viewing of a locked folder

Test verifies that a non-admin user without permissions sees a locked folder and everything inside it as read-only, with all edit/create/delete affordances hidden or disabled.

**Preparations**:
1. Perform the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as another non-admin user, different from the one used in the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case |  |
| 2 | Open **Library** page | At the library-tree, in front of the folder (created at step 3 of [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case), pipeline (created at step 13), storage (created at step 15), configuration (created at step 17) and folder (created at step 18), there are lock icons |
| 3 | Navigate to the folder created at step 3 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case | <li> **Upload metadata**, **+ Create v** buttons don't display <li> next to the pipeline, storage, configuration **Edit** buttons don't display <li> next to the pipeline the **RUN** button doesn't display <li> next to the folder (created at step 18) the **Delete** button doesn't display |
| 4 | Click the gear icon in the right upper corner | Only the **Permissions** item appears in the dropdown list |
| 5 | Click **Permissions** item | In the appeared pop-up, the **Name** field is disabled |
| 6 | Click **Permissions** tab in the appeared pop-up | <li> **Add user**, **Add group** buttons are disabled <li> only the `ROLE_USER` role is displayed <li> the **Delete** button next to `ROLE_USER` is disabled |
| 7 | Click on the `ROLE_USER` item | All permission checkboxes are disabled for clicking |
| 8 | Close the pop-up |  |
| 9 | Click on the pipeline created at step 13 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case | **RELEASE**, **RUN** buttons don't display |
| 10 | Click on the pipeline version | <li> **RUN** button doesn't display <li> **Upload**, **Rename**, **Delete**, **EDIT** buttons for `README.MD` don't display |
| 11 | Repeat step 3 |  |
| 12 | Click on the storage created at step 15 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case | <li> **+ Create v**, **Upload** buttons don't display <li> **Show files versions** checkbox doesn't display |
| 13 | Repeat step 3 |  |
| 14 | Click on the configuration created at step 17 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case | <li> **+ ADD**, **Run**, **Save** buttons don't display <li> **Name** field is disabled |
| 15 | Repeat step 3 |  |
| 16 | Click on the folder created at step 18 of the [EPMCMBIBPC-2684](EPMCMBIBPC-2684.md) case | **Upload metadata**, **+ Create v** buttons don't display |
