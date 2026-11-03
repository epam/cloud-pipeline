# [MANUAL] Validation exception if user use system parameters names for non-system parameter

Test verifies that entering a system-parameter name for a regular configuration parameter is rejected and is not saved.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-1678](EPMCMBIBPC-1678.md) case |  |
| 2 | Open the pipeline created in the [EPMCMBIBPC-1676](EPMCMBIBPC-1676.md) case |  |
| 3 | Go to the **CONFIGURATION** tab |  |
| 4 | Click the delete icon near the system parameter |  |
| 5 | Click **Save** button |  |
| 6 | Click the dropdown button near the **Add parameter** button |  |
| 7 | Select **String parameter** |  |
| 8 | Enter `param1` into the **Name** field |  |
| 9 | Enter `param2` into the **Name** field |  |
| 10 | Enter `param3` into the **Name** field |  |
| 11 | Click **Save** button | The message `Name is reserved for system parameter` is displayed under the parameter's **Name** field |
| 12 | Refresh the page | After refreshing, the parameter is not saved |
| 13 | Repeat steps 6-12 for all parameter types |  |
