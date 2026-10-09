# Validation of HISTORY tab

Test verifies the columns shown for completed-run records on the HISTORY tab.

**Prerequisites**:
- An existing pipeline that has at least one completed run

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Click on the pipeline that has at least one finished run |  |
| 3 | Select the pipeline version |  |
| 4 | Click on **HISTORY** tab | The table of completed-run records appears; each record contains: <li> the completed-status icon and run ID <li> pipeline name and version <li> docker image name <li> started time <li> completed time <li> elapsed time and summary cost <li> owner name <li> **RERUN**, **LOG** hyperlinks |
