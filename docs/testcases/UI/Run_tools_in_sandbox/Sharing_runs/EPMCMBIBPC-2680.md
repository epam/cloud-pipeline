# Displaying "sharing" tool at "Services" panel

Test verifies that a shared run's tile appears on the Home page's SERVICES panel for the user it was shared with, and disappears once sharing is revoked.

**Prerequisites**:
- Login as the admin who launched the run at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case, don't stop the launched run |  |
| 2 | Open the **Runs** page |  |
| 3 | On the **ACTIVE RUNS** page click the run launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 4 | On the appeared run logs page, copy the **Endpoint** link address |  |
| 5 | Click the **Not shared (click to configure)** link |  |
| 6 | Click the **Add user** icon in the appeared pop-up |  |
| 7 | Specify the name of a non-admin user in the appeared pop-up, click **OK** button |  |
| 8 | Click **OK** button |  |
| 9 | Logout |  |
| 10 | Login as the non-admin user from step 7 |  |
| 11 | Open the **Home** page |  |
| 12 | Click the **Configure** button in the right upper corner |  |
| 13 | Set the **Services** checkbox in the appeared pop-up if it's unset, click **OK** button | At the **SERVICES** panel, the **Endpoint** link is displayed, and near it: the registry, group and name of the tool launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case; the run ID of the pipeline launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |
| 14 | Click the **Endpoint** link at the **SERVICES** panel | A new tab is opened containing an application from the tool (for e2e-endpoints: the page with the user name who opened the link appears) |
| 15 | Close the opened tab |  |
| 16 | Logout |  |
| 17 | Login as the admin who launched the run at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 18 | Repeat steps 2-3 |  |
| 19 | Click the link next to the **Share with** label |  |
| 20 | Click the **Delete** icon near the user name specified at step 7 in the appeared pop-up |  |
| 21 | Repeat steps 8-11 | The label `There are no services` is displayed at the **SERVICES** panel |
