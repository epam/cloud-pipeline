# [MANUAL] Validation of auto-scroll pipeline logs

Test verifies that with **Follow log** enabled, the log view auto-scrolls to always show the latest line.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a SHELL pipeline |  |
| 2 | Go into the created pipeline |  |
| 3 | Edit the `*.sh` file |  |
| 4 | Paste the code from the attached file |  |
| 5 | Save and commit |  |
| 6 | Launch the pipeline |  |
| 7 | Open the logs page |  |
| 8 | Make sure the **Follow log** checkbox is enabled | The user always sees the last line of the logs |
