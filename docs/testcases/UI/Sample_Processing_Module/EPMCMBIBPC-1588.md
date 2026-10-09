# Validation of add detached configuration in project folder

Test verifies creating a detached configuration inside a project folder and its default layout.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1412](EPMCMBIBPC-1412.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the project folder created at step 4 of the [EPMCMBIBPC-1411](EPMCMBIBPC-1411.md) case |  |
| 2 | Hover over **+ Create v** |  |
| 3 | In the list that appears, click **Configuration** |  |
| 4 | Enter a valid configuration name |  |
| 5 | Click **CREATE** |  |
| 6 | Click on the created configuration | The detached configuration page appears, containing: <li>a title with the name entered at step 4 <li>the **default** tab <li>the **+ ADD**, **Run**, **Save** buttons <li>the **Name** text field <li>the **Exec environment** collapsed header (expanded by default), containing: the **Pipeline**, **Docker image**, **Disk (Gb)** fields; the **Node type** combobox; the **Configure cluster** hyperlink <li>the **Advanced** collapsed header (collapsed by default) <li>the **Parameters** collapsed header (expanded by default), containing: the **Root entity type** combobox; the **Add parameter** button |
