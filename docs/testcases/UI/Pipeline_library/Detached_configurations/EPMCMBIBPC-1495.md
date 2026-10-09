# Configuration edit pop-up focus validation

Test verifies that the edit-configuration pop-up opens with focus on the name field and that pressing Enter renames the configuration.

**Prerequisites**:
- Perform the [EPMCMBIBPC-1091](EPMCMBIBPC-1091.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the configuration from the prerequisites |  |
| 2 | Press the edit button (gear icon located in the upper-right corner) | The **Edit configuration info** pop-up appears with focus on the **Configuration name** field (cursor is already there, field edges are highlighted blue) |
| 3 | Start modifying the configuration name |  |
| 4 | Press **Enter** key when finished | The new configuration name appears in the tree instead of the old one |
