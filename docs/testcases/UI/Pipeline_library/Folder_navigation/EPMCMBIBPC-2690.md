# [MANUAL] Combine lock and unlock

Test verifies locking a nested folder structure, then unlocking an inner folder while the outer ones stay locked, and that a non-admin user's view reflects the mixed lock state.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button -> click **Folder** in the appeared list |  |
| 3 | In the appeared pop-up input a valid folder name, click **OK** button |  |
| 4 | Open the just-created folder |  |
| 5 | Repeat steps 2-3 |  |
| 6 | Repeat steps 2-3 |  |
| 7 | Hover over the gear icon -> click **Edit folder** in the appeared list |  |
| 8 | Click **Permissions** tab |  |
| 9 | Click **Add group** button |  |
| 10 | In the appeared pop-up input `ROLE_USER`, click **OK** button |  |
| 11 | In the **Groups and users** table click on `ROLE_USER` |  |
| 12 | Set the **Allow** checkboxes for **Read**, **Write** and **Execute** permissions |  |
| 13 | Close the pop-up |  |
| 14 | Hover over the gear icon -> click **Lock** in the appeared list |  |
| 15 | Click **OK** button in the appeared pop-up |  |
| 16 | Open the folder created at step 6 |  |
| 17 | Hover over the gear icon -> click **Unlock** in the appeared list |  |
| 18 | Click **OK** button in the appeared pop-up |  |
| 19 | Repeat steps 7-13 |  |
| 20 | Logout |  |
| 21 | Login as another, non-admin user |  |
| 22 | Open **Library** page |  |
| 23 | Navigate to the folder created at step 3 | At the library-tree: <li> in front of the folders created at steps 3 and 5, there are lock icons <li> in front of the folder created at step 6, no lock icon is displayed |
| 24 | Open the folder created at step 5 | **Upload metadata**, **+ Create v** buttons don't display |
| 25 | Click the gear icon in the right upper corner | Only the **Permissions** item is in the appeared list |
| 26 | Open the folder created at step 6 | **Upload metadata**, **+ Create v** buttons don't display |
| 27 | Click the gear icon in the right upper corner | The following items appear in the appeared list: **Edit folder**, **Lock** |
| 28 | Click **Edit folder** in the appeared list | In the appeared pop-up, at the **Info** tab, the **Name** field is enabled |
| 29 | Click **Permissions** tab | <li> **Add user**, **Add group** buttons are disabled <li> only the `ROLE_USER` role is displayed in the permissions table <li> the **Delete** button next to `ROLE_USER` is disabled |
| 30 | Click on the `ROLE_USER` item | All permission checkboxes are disabled for clicking |
