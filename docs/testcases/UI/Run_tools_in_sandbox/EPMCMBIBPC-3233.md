# [MANUAL] "Run as" for general users

*Note: This testcase is available only for the AWS deployment.*

Test verifies that a pipeline configured with `run_as` and `share_with_roles` runs under the configured owner while attributing the original launcher and sharing the run with the configured role.

**Prerequisites**:
- Admin user
- Non-admin user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites |  |
| 2 | Open the **System Settings** window |  |
| 3 | Select the **USER MANAGEMENT** tab |  |
| 4 | Click the **Users** tab |  |
| 5 | Click the admin user from the prerequisites in the table |  |
| 6 | Click the **configure** button next to **Can run as this user:** |  |
| 7 | Click the **Add user** button |  |
| 8 | Specify the existing non-admin name from the prerequisites |  |
| 9 | Click the **OK** button |  |
| 10 | Click the **OK** button |  |
| 11 | Save changes with the **OK** button |  |
| 12 | Open the **Library** page |  |
| 13 | Create a test pipeline from a default template |  |
| 14 | Open the test pipeline created at step 13 |  |
| 15 | Hover over the gear icon in the right upper corner |  |
| 16 | Click the **Edit** button in the list |  |
| 17 | Click the **Permissions** tab |  |
| 18 | Click the **Add user** icon |  |
| 19 | Specify the existing non-admin name from the prerequisites |  |
| 20 | Click the **OK** button |  |
| 21 | Click the appeared username in the list at the **Permissions** tab |  |
| 22 | Set the checkbox in the **Allow** column next to **Read**, **Write** and **Execute** permissions in the appeared table |  |
| 23 | Close the pop-up |  |
| 24 | Click a pipeline version |  |
| 25 | Select the **CODE** tab |  |
| 26 | Click the `config.json` file |  |
| 27 | Click the **EDIT** button |  |
| 28 | In the `configuration` section specify: `"run_as": "{admin_user_name}, "share_with_roles": [{"name": "ROLE_USER"}]"` where `{admin_user_name}` is the admin name from the prerequisites |  |
| 29 | Click the **SAVE** button |  |
| 30 | Specify a commit message in the appeared pop-up |  |
| 31 | Click the **Commit** button |  |
| 32 | Logout |  |
| 33 | Login as the non-admin user from the prerequisites whose name was specified at step 21 |  |
| 34 | Open the **Library** page |  |
| 35 | Click the test pipeline created at step 13 |  |
| 36 | Click the **RUN** button |  |
| 37 | Specify valid values on the **Launch** page |  |
| 38 | Click the **Launch** button |  |
| 39 | Click the **Launch** button in the appeared pop-up |  |
| 40 | Click the launched pipeline run that appears in the **ACTIVE RUNS** tab of the **Runs** page | The **Owner** field of the run matches the admin name from the prerequisites |
| 41 | Logout |  |
| 42 | Login as the admin user from the prerequisites |  |
| 43 | Open the **Runs** page |  |
| 44 | Click the launched pipeline run started at step 39 | The **Share with** field of the pipeline run contains: <li> the non-admin user from the prerequisites <li> the role `ROLE_USER` |
| 45 | Click the **Parameters** tab | The **ORIGINAL_OWNER** parameter of the pipeline run contains the non-admin user from the prerequisites |

**After**:
- Stop the run launched at step 39
- Remove the pipeline created at step 13
- Remove the non-admin username from the "User settings" configured for the admin user at steps 6-11
