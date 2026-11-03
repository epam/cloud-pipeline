# Share tool run with user

Test verifies sharing a run's endpoint with a specific user, then revoking that sharing.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case, don't stop the launched run |  |
| 2 | Open the **Runs** page |  |
| 3 | On the **ACTIVE RUNS** page click the run launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 4 | On the appeared run logs page copy the **Endpoint** link address |  |
| 5 | Click the **Not shared (click to configure)** link |  |
| 6 | Click the **Add user** icon in the appeared pop-up |  |
| 7 | Specify the name of a non-admin user in the appeared pop-up, click **OK** button | Next to the **Share with** label, a link with the user name specified at step 7 is displayed |
| 8 | Click **OK** button |  |
| 9 | Logout |  |
| 10 | Login as the non-admin user from step 7 |  |
| 11 | Open a new browser tab, enter the link copied at step 4 into the address bar, press **Enter** | An application from the tool appears (for e2e-endpoints: the page with the user name who opened the link appears) |
| 12 | Close the opened tab |  |
| 13 | Logout |  |
| 14 | Login as the admin who launched the run at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 15 | Repeat steps 2-3 |  |
| 16 | Click the link next to the **Share with** label | The "Share with users and groups" pop-up appears, containing: <li> the user name specified at step 7 <li> a **Delete** icon next to the user name |
| 17 | Click the **Delete** icon near the user name specified at step 7 |  |
| 18 | Click **OK** button | Next to the **Share with** label, the link `Not shared (click to configure)` is displayed |
| 19 | Repeat steps 9-11 | A `401 Authorization Required` error message appears |
