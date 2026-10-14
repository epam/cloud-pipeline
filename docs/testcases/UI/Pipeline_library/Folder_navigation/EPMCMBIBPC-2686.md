# [MANUAL] Lock Project

Test verifies that locking a Project shows lock icons on the Project folder and its Method-Configurations folder and storage, and disables edit permissions for the role that had them.

**Preparations**:
1. Login as non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Hover over **+ Create v** button -> click **PROJECT** button in the appeared list |  |
| 3 | In the appeared pop-up, input a valid Project name, click **OK** button |  |
| 4 | Open the just-created Project |  |
| 5 | Hover over the gear icon in the right upper corner -> click **Edit folder** in the appeared list |  |
| 6 | Click **Permissions** tab |  |
| 7 | Click **Add group** button |  |
| 8 | In the appeared pop-up, input `ROLE_USER`, click **OK** button |  |
| 9 | In the **Groups and users** table click on the `ROLE_USER` item |  |
| 10 | Set the **Allow** checkboxes for **Read**, **Write** and **Execute** permissions |  |
| 11 | Close the pop-up |  |
| 12 | Hover over the gear icon in the right upper corner -> click **Lock** in the appeared list | Pop-up appears with the text `Are you sure you want to lock folder {Project_name}`, where `{Project_name}` equals the value specified at step 3 |
| 13 | Click **OK** button in the appeared pop-up | At the library-tree, in front of the `{Project_name}` folder (created at step 3), the **Method-Configurations** folder, and the `{Project_name}-storage` storage, there are lock icons |
| 14 | Repeat steps 5-6 | At the **Permissions** tab: <li> **Add user**, **Add group** buttons are disabled <li> only the `ROLE_USER` role is displayed <li> the **Delete** button next to `ROLE_USER` is disabled |
| 15 | Click on the `ROLE_USER` item | All permission checkboxes are disabled for clicking |
| 16 | Close the pop-up |  |
