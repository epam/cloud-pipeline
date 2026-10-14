# [MANUAL] System parameters validating on pipeline log page

Test verifies that a system parameter selected on the launch form shows up correctly on the running pipeline's Parameters section, and that its value is a working hyperlink.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | After performing the [EPMCMBIBPC-1677](EPMCMBIBPC-1677.md) case |  |
| 2 | Click **Launch** button |  |
| 3 | Go to the launched pipeline's page |  |
| 4 | Click the collapse header **Parameters** |  |
| 5 | Click the value `param2` | <li> 2 parameters are displayed: `param1` (value matches the one entered at step 8 of the [EPMCMBIBPC-1676](EPMCMBIBPC-1676.md) case), `param2` (hyperlink, value matches the one entered at step 7 of the [EPMCMBIBPC-1677](EPMCMBIBPC-1677.md) case) <li> after step 5, the storage specified at step 7 of the [EPMCMBIBPC-1677](EPMCMBIBPC-1677.md) case opens |
