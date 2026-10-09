# [MANUAL] Lock folder

Test verifies that locking a folder shows lock icons on it and everything inside it, and disables edit permissions for the role that had them.

**Preparations**:
1. Login as non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button -> click **Folder** button in the appeared list |  |
| 3 | In the appeared pop-up, input a valid folder name, click **OK** button |  |
| 4 | Open the just-created folder |  |
| 5 | Hover over the gear icon in the right upper corner -> click **Edit folder** in the appeared list |  |
| 6 | Click **Permissions** tab |  |
| 7 | Click **Add group** button |  |
| 8 | In the appeared pop-up, input `ROLE_USER`, click **OK** button |  |
| 9 | In the **Groups and users** table click on the `ROLE_USER` item |  |
| 10 | Set the **Allow** checkboxes for **Read**, **Write** and **Execute** permissions |  |
| 11 | Close the pop-up |  |
| 12 | Hover over **+ Create v** button -> click **Pipeline** button in the appeared list |  |
| 13 | In the appeared pop-up, input a valid pipeline name, click **CREATE** button |  |
| 14 | Hover over **+ Create v** button -> click **Storages** button in the appeared list |  |
| 15 | In the appeared pop-up, input a valid storage name (path), click **Create** button |  |
| 16 | Hover over **+ Create v** button -> click **Configuration** button in the appeared list |  |
| 17 | In the appeared pop-up, input a valid configuration name, click **CREATE** button |  |
| 18 | Repeat steps 2-3 |  |
| 19 | Hover over the gear icon in the right upper corner -> click **Lock** in the appeared list | Pop-up appears with the text `Are you sure you want to lock folder {folder_name}`, where `{folder_name}` equals the value specified at step 3 |
| 20 | Click **OK** button in the appeared pop-up | At the library-tree, in front of the folder (created at step 3), pipeline (created at step 13), storage (created at step 15), configuration (created at step 17) and folder (created at step 18), there are lock icons |
| 21 | Repeat steps 5-6 | At the **Permissions** tab: <li> **Add user**, **Add group** buttons are disabled <li> only the `ROLE_USER` role is displayed in the permissions table <li> the **Delete** button next to `ROLE_USER` is disabled |
| 22 | Click on the `ROLE_USER` item | All permission checkboxes are disabled for clicking |
| 23 | Close the pop-up |  |
