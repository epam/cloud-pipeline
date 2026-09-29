# [MANUAL] [Negative] validation of unauthorized access

Test verifies that a shared-storage link is rejected with Access denied once all permissions for the linked user are denied.

**Prerequisites**:
- Perform [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case
- A non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the storage created in the [EPMCMBIBPC-2401](EPMCMBIBPC-2401.md) case |  |
| 3 | Click the "gear" icon in the right upper corner |  |
| 4 | Click the **Permissions** tab |  |
| 5 | Find the non-admin user from the prerequisites |  |
| 6 | Deny all permissions for that user |  |
| 7 | Close the pop-up |  |
| 8 | Click the **Share** button |  |
| 9 | Save the link from the appeared pop-up, close the pop-up |  |
| 10 | Logout |  |
| 11 | Enter the link saved at step 8 into the address bar, press **Enter** |  |
| 12 | Login as the non-admin user from the prerequisites | An `Access denied` error is displayed |
