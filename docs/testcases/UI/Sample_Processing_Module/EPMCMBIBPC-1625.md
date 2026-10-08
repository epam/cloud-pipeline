# "Select metadata"-"SampleSet" pop-up validation

Test verifies the Select metadata pop-up's layout when launching a SampleSet-rooted configuration.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1624](EPMCMBIBPC-1624.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the detached configuration edited in the [EPMCMBIBPC-1624](EPMCMBIBPC-1624.md) case |  |
| 2 | Click **Run** | The **Select metadata** pop-up appears, containing: <li>a left panel with the library tree (hierarchy: {project name} -> "Metadata") <li>a right panel with the {project name} folder containing the "Metadata" folder <li>the **Define expressions** field <li>the **Clear selection**, **Cancel**, **OK** buttons |
| 3 | Click **Metadata** in the library tree on the left panel of the pop-up | A list expands, containing the **Sample** and **SampleSet** items |
| 4 | Click **SampleSet** in the list on the right panel of the pop-up | On the right panel, a table appears with the rows: <li>ID `NA12878_11_rep`, Name "11 replicates of NA12878", Samples "11 Sample(s)" <li>ID `NA12878_3_rep`, Name "3 replicates of NA12878", Samples "3 Sample(s)" |
