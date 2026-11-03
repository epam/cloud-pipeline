# [MANUAL] Viewing of a locked Project

Test verifies that a non-admin user without permissions sees a locked Project's folder, Method-Configurations folder and storage as read-only.

**Preparations**:
1. Perform the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as another non-admin user, different from the one used in the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case |  |
| 2 | Open **Library** page | At the library-tree, in front of the `{Project_name}` folder (created at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case), **Method-Configurations** folder and `{Project_name}-storage` storage, there are lock icons |
| 3 | Navigate to the Project folder created at step 3 of the [EPMCMBIBPC-2686](EPMCMBIBPC-2686.md) case | <li> **Upload metadata**, **+ Create v** buttons don't display <li> at the **Attributes** panel: **+ Add**, **Remove all** buttons don't display; **Delete** buttons for the attributes don't display <li> next to the `{Project_name}-storage` the **Edit** button doesn't display <li> next to **Method-Configurations** folder the **Delete** button doesn't display |
| 4 | Click the gear icon | Only the **Permissions** item appears in the dropdown list |
| 5 | Click **Permissions** item | In the appeared pop-up, the **Name** field is disabled |
| 6 | Click **Permissions** tab in the appeared pop-up | <li> **Add user**, **Add group** buttons are disabled <li> only the `ROLE_USER` role is displayed <li> the **Delete** button next to `ROLE_USER` is disabled |
| 7 | Click on the `ROLE_USER` item | All permission checkboxes are disabled for clicking |
| 8 | Close the pop-up |  |
| 9 | Click on the **Method-Configurations** folder | **Upload metadata**, **+ Create v** buttons don't display |
| 10 | Repeat step 3 |  |
| 11 | Click on the `{Project_name}-storage` storage | <li> **+ Create v**, **Upload** buttons don't display <li> **Show files versions** checkbox doesn't display |
