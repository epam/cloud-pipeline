# [MANUAL] Validation exception if user use system parameters names for non-system parameter on launch form

Test verifies that entering a system-parameter name for a regular launch parameter is rejected and blocks the launch.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-1679](EPMCMBIBPC-1679.md) case |  |
| 2 | Click **Run** button |  |
| 3 | Click the dropdown button near the **Add parameter** button |  |
| 4 | Select **String parameter** |  |
| 5 | Enter `param1` into the **Name** field |  |
| 6 | Enter `param2` into the **Name** field |  |
| 7 | Enter `param3` into the **Name** field |  |
| 8 | Click **Launch** button | <li> the pipeline is not launched <li> the message `Name is reserved for system parameter` is displayed under the parameter's **Name** field |
| 9 | Refresh the page | After refreshing, the parameter is not saved |
| 10 | Repeat steps 3-9 for all parameter types |  |
