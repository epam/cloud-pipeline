# [MANUAL] Validation of disable auto-scroll pipeline logs

Test verifies that disabling **Follow log** stops the log view from auto-scrolling to new records.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-7 of the [EPMCMBIBPC-1742](EPMCMBIBPC-1742.md) case |  |
| 2 | Uncheck the **Follow log** checkbox | When new records appear, the user does not see content below the bottom of the viewport |
