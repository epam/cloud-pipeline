# Auto-scaled cluster: workers price type (Spot)

Test verifies that, with the workers price type set to Spot, a cluster's worker uses Spot regardless of the launch form's own price type.

**Prerequisites**:
- Some names can differ for each cloud provider

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-10 of the [EPMCMBIBPC-2618](EPMCMBIBPC-2618.md) case |  |
| 2 | Select the **Spot** value in the **Workers price type** dropdown list |  |
| 3 | Click the **OK** button |  |
| 4 | Click the **Advanced** section to expand |  |
| 5 | Select the **On-demand** value in the **Price type** dropdown list |  |
| 6 | Click the **Launch** button |  |
| 7 | Click the **Launch** button in the appeared pop-up window |  |
| 8 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab | The price type shown in the **Instance** section is **On-demand** |
| 9 | Wait until the label **Nested runs** appears |  |
| 10 | Click the link with the run ID near the label **Nested runs** | The price type shown in the **Instance** section is **Spot** |
| 11 | Stop the nested run |  |
| 12 | Stop the parent run |  |
