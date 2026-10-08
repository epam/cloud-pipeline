# "Select metadata" pop-up validation

Test verifies the Select metadata pop-up's layout when launching a Sample-rooted configuration.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1595](EPMCMBIBPC-1595.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the detached configuration edited at step 13 of the [EPMCMBIBPC-1595](EPMCMBIBPC-1595.md) case |  |
| 2 | Click **Run** | The **Select metadata** pop-up appears, containing: <li>a left panel with the library tree (hierarchy: {project name} -> "Metadata") <li>a right panel with the {project name} folder containing the "Metadata" folder <li>the **Define expressions** field <li>the **Clear selection**, **Cancel**, **OK** buttons |
| 3 | Click **Metadata** in the library tree on the left panel of the pop-up | A list expands, containing the **Sample** and **SampleSet** items |
| 4 | Click **Sample** in the list on the right panel of the pop-up | On the right panel, the list of files created at steps 13, 14, 16 of the [EPMCMBIBPC-1519](EPMCMBIBPC-1519.md) case appears |
