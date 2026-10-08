# Validation of changes in attached pipeline configuration

Test verifies that Exec environment/Advanced field changes made in a detach configuration are saved.

**Prerequisites**:
- Existing pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case |  |
| 2 | Click on the created detach configuration |  |
| 3 | Click on the field opposite the **Pipeline** label |  |
| 4 | Select the pipeline from the prerequisites in the appeared pop-up window |  |
| 5 | Click on the pipeline version |  |
| 6 | Click **OK** button |  |
| 7 | Click **Yes** in the appeared pop-up window |  |
| 8 | Expand the **Exec environment** and **Advanced** collapsed sections |  |
| 9 | Change the disk size in the **Disk (Gb)** field |  |
| 10 | Change the instance type in the **Node type** field |  |
| 11 | Select a price type in the **Price type** combobox |  |
| 12 | Click **Save** button | All configuration changes are saved |
