# Validation of tools instance types restrictions (hierarchy)

Test verifies that a per-tool instance-types mask applied via Instance management takes precedence over the user's own tool-instance-types mask.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2646](EPMCMBIBPC-2646.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Preferences** tab in system settings |  |
| 3 | Click the **Cluster** menu item on the left panel |  |
| 4 | Specify a mask value into **cluster.allowed.instance.types.docker** (e.g. `m5.*`) |  |
| 5 | Clear the mask that was changed at step 8 of the EPMCMBIBPC-2646 case |  |
| 6 | Perform steps 2-10 of the [EPMCMBIBPC-2642](EPMCMBIBPC-2642.md) case | A drop-down list appears with only the values of node types that comply with the mask entered while performing step 6 of the EPMCMBIBPC-2642 case |
| 7 | Perform steps 11-14 of the [EPMCMBIBPC-2642](EPMCMBIBPC-2642.md) case | A drop-down list appears with only the values of node types that comply with the mask entered at step 13 of the EPMCMBIBPC-2646 case |

**After**:
- Clear the masks that were changed at steps 4, 12, 13 of the EPMCMBIBPC-2646 case and steps 4, 6 of the current case
