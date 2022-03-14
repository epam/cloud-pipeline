# Check Pipe CLI User Run As User

Test verifies that a user with sufficient permissions can launch a run as a different user using pipe CLI.

**Prerequisites**:
- Admin user
- Regular user

| Steps | Actions                                                                                            | Expected results |
|:-----:|----------------------------------------------------------------------------------------------------| -- |
|   1   | Login as the admin user from the prerequisites                                                     | |
|   2   | Open the Settings page                                                                             | |
|   3   | Open the User Management tab                                                                       | |
|   4   | Find the regular user from the prerequisites and open their settings                               | |
|   5   | Add the regular user to the list of users who can run as the admin user                            | |
|   6   | Save changes                                                                                       | |
|   7   | Login as the regular user from the prerequisites                                                   | |
|   8   | Open the Tools page                                                                                | |
|   9   | Select any tool                                                                                    | |
|  10   | Run tool with *Custom settings*                                                                    | |
|  11   | At the Runs page, click the just-launched run                                                      | |
|  12   | Wait until the SSH hyperlink appears                                                               | |
|  13   | Click the SSH hyperlink                                                                            | |
|  14   | In the opened tab, enter and perform the `pipe run -di library/centos:latest -u <ADMIN USER> -y`   | |
|  15   | Open the Runs page                                                                                 | |
|  16   | Click the just-launched run                                                                        | The owner of the run is the admin user. The run has ORIGINAL_OWNER parameter set to the regular user. |
