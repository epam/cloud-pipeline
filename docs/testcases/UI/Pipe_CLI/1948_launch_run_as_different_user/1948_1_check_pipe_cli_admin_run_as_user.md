# Check Pipe CLI Admin Run As User

Test verifies that an admin can launch a run as a different user using pipe CLI.

**Prerequisites**:
- Admin user
- Regular user

| Steps | Actions                                                                                              | Expected results |
|:-----:|------------------------------------------------------------------------------------------------------| -- |
|   1   | Login as the admin user from the prerequisites                                                       | |
|   2   | Open the Tools page                                                                                  | |
|   3   | Select any tool                                                                                      | |
|   4   | Run tool with *Custom settings*                                                                      | |
|   5   | At the Runs page, click the just-launched run                                                        | |
|   6   | Wait until the SSH hyperlink appears                                                                 | |
|   7   | Click the SSH hyperlink                                                                              | |
|   8   | In the opened tab, enter and perform the `pipe run -di library/centos:latest -u <REGULAR USER> -y`   | |
|   9   | Open the Runs page                                                                                   | |
|  10   | Click the just-launched run                                                                          | The owner of the run is USER3. The run does not have ORIGINAL_OWNER parameter specified. |
