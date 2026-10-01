# [MANUAL] Validation of read-only user access to shared storage

Test verifies that a user with only read permission on a shared storage can view its content but has no edit/remove actions.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform [EPMCMBIBPC-2405](EPMCMBIBPC-2405.md) case |  |
| 2 | Click the "gear" icon in the right upper corner |  |
| 3 | Click the **Permissions** tab |  |
| 4 | Find the non-admin user from the prerequisites of the [EPMCMBIBPC-2402](EPMCMBIBPC-2402.md) case |  |
| 5 | Allow 'read' and deny 'write' permissions for that user |  |
| 6 | Close the pop-up |  |
| 7 | Repeat steps 7-11 of the [EPMCMBIBPC-2402](EPMCMBIBPC-2402.md) case | The storage page opens, containing: <li> the name of the storage created in the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case <li> the **Refresh** button <li> the file and folder created in the [EPMCMBIBPC-2403](EPMCMBIBPC-2403.md) case are displayed in the list <li> the **Edit**, **Remove** buttons near the files/folders are missing |
