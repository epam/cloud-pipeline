# [MANUAL] Share SSH session

Test verifies sharing SSH access to a run with a specific user, then revoking it.

**Prerequisites**:
- Login as the admin who launched the run at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case
- Check that the system preference `system.ssh.default.root.user.enabled` is set and visible (the eye icon near the preference is set)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case, don't stop the launched run |  |
| 2 | Open the **Runs** page |  |
| 3 | On the **ACTIVE RUNS** page click the run launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 4 | Click the **Not shared (click to configure)** link |  |
| 5 | Click the **Add user** icon in the appeared pop-up |  |
| 6 | Specify the name of a non-admin user in the appeared pop-up, click **OK** button |  |
| 7 | Set the **Enable SSH connection** checkbox near the user name |  |
| 8 | Click **OK** button | Next to the **Share with** label, a link with the user name specified at step 6 is displayed |
| 9 | Click the **SSH** button |  |
| 10 | In the appeared tab specify and run the command: `echo 123 > test.file` |  |
| 11 | Close the tab |  |
| 12 | Logout |  |
| 13 | Login as the non-admin user from step 6 |  |
| 14 | Open the **Home** page |  |
| 15 | If the **SERVICES** panel isn't displayed: click the **Configure** button in the right upper corner; in the appeared pop-up set the **Services** checkbox, click **OK** button | At the **SERVICES** panel, the tile with the name equal to the tool launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case is displayed |
| 16 | At the **SERVICES** panel, hover over the tile with the name equal to the tool launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case | The **SSH** hyperlink appears |
| 17 | Click the appeared **SSH** link |  |
| 18 | In the appeared tab specify and run the command: `cat test.file` | The command output is `123` |
| 19 | Close the opened tab |  |
| 20 | Logout |  |
| 21 | Login as the admin who launched the run at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case |  |
| 22 | Repeat steps 2-3 |  |
| 23 | Click the link next to the **Share with** label |  |
| 24 | Uncheck the **Enable SSH connection** checkbox |  |
| 25 | Click **OK** button |  |
| 26 | Repeat steps 12-14 | At the **SERVICES** panel, the tile with the name equal to the tool launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case is displayed |
| 27 | At the **SERVICES** panel, hover over the tile with the name equal to the tool launched at step 9 of the [EPMCMBIBPC-2674](EPMCMBIBPC-2674.md) case | The **SSH** hyperlink doesn't appear |
