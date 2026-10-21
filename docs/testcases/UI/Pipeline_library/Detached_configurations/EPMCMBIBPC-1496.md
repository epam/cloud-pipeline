# Add configuration pop-up focus validation

Test verifies that the create-configuration pop-up opens with focus on the name field and that pressing Enter creates a new tab.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the configuration from the prerequisites |  |
| 2 | Press **+ ADD** button located in the upper-right corner | The **Create configuration** pop-up appears with focus on the **Name** field (cursor is already there, field edges are highlighted blue) |
| 3 | Start entering a new configuration name |  |
| 4 | Press **Enter** key when finished | A new tab with the corresponding name specified at step 3 appears |
