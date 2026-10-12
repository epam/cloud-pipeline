# Add existing in repository pipeline

Test verifies re-adding a pipeline by pointing a new pipeline's Git repository at a previously unregistered one.

**Prerequisites**:
- Perform the [EPMCMBIBPC-707](EPMCMBIBPC-707.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Hover over **+ Create v**, click **Pipeline** in the appeared dropdown list |  |
| 2 | Click on **Edit repository settings** in the appeared window |  |
| 3 | Paste into the **Repository** field the value copied at step 4 of the [EPMCMBIBPC-707](EPMCMBIBPC-707.md) case |  |
| 4 | Click **CREATE** button | A new pipeline appears with the same files as were in the pipeline removed at the [EPMCMBIBPC-707](EPMCMBIBPC-707.md) case |
