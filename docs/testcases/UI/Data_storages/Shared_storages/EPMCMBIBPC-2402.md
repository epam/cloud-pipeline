# [MANUAL] Check access to shared bucket

Test verifies that a non-admin user with read/write permissions can open a shared-bucket link and see its content.

**Prerequisites**:
- Perform [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case
- A non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the storage created in the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case |  |
| 2 | Click the "gear" icon in the right upper corner |  |
| 3 | Click the **Permissions** tab |  |
| 4 | Find the non-admin user from the prerequisites |  |
| 5 | Add 'read' and 'write' permissions for that user |  |
| 6 | Close the pop-up |  |
| 7 | Click the **Share** button |  |
| 8 | Save the link from the appeared pop-up, close the pop-up |  |
| 9 | Logout |  |
| 10 | Enter the link saved at step 8 into the address bar, press **Enter** |  |
| 11 | Login as the non-admin user from the prerequisites | The storage page opens, containing: <li> the name of the storage created in the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case <li> buttons: **Refresh**, **+ Create folder**, **Upload** <li> the folder created at steps 9-10 of the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case is displayed in the list <li> the file uploaded at step 11 of the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case is displayed in the list |
