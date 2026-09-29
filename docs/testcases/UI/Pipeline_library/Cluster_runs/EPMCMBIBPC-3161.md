# Auto-scaled cluster: workers price type (Master's config)

Test verifies that, with the workers price type set to Master's config, a cluster's worker inherits the master's price type regardless of the launch form's own price type.

**Prerequisites**:
- Some names can differ for each cloud provider

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat step 1 of the [EPMCMBIBPC-3156](EPMCMBIBPC-3156.md) case |  |
| 2 | Select the **Master's config** value in the **Workers price type** dropdown list |  |
| 3 | Click the **OK** button |  |
| 4 | Click the **Advanced** section to expand |  |
| 5 | Select the **Spot** value in the **Price type** dropdown list |  |
| 6 | Click the **Launch** button |  |
| 7 | Click the **Launch** button in the appeared pop-up window |  |
| 8 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab | The price type shown in the **Instance** section is **Spot** |
| 9 | Wait until the label **Nested runs** appears |  |
| 10 | Click the link with the run ID near the label **Nested runs** | The price type shown in the **Instance** section is **Spot** |
| 11 | Stop the nested run |  |
| 12 | Stop the parent run |  |
