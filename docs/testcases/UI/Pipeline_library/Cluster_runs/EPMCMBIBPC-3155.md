# Hybrid auto-scaled cluster: instance type family

Test verifies that the `CP_CAP_AUTOSCALE_HYBRID_FAMILY` parameter makes the auto-scaled child node come from the specified instance family, distinct from the master's own type.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-5 of the [EPMCMBIBPC-975](EPMCMBIBPC-975.md) case |  |
| 2 | Input a valid value into the **Node type** field (e.g. `m5.large`) |  |
| 3 | Input a valid value into the **Disk (Gb)** field |  |
| 4 | Click the **Configure cluster** control |  |
| 5 | Select the **Auto-scaled cluster** tab |  |
| 6 | Set the **Enable Hybrid cluster** checkbox |  |
| 7 | Click **OK** button |  |
| 8 | Click the **Advanced** section |  |
| 9 | Set the **Cmd template** to: `qsub -b y -t 1:10 sleep 15m && sleep infinity` |  |
| 10 | Click the **Add system parameter** button |  |
| 11 | Select the row `CP_CAP_AUTOSCALE_HYBRID_FAMILY` in the list |  |
| 12 | Click **OK** button |  |
| 13 | Specify an instance family name into the value field, different from the one selected at step 2 (e.g. `c5`) |  |
| 14 | Click the **Launch** button |  |
| 15 | Click the **Launch** button in the appeared pop-up window |  |
| 16 | Click the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 17 | Wait until the **Nested runs** label with the run ID appears |  |
| 18 | Click the nested run ID link | The family of the run displayed on the **Run logs** page equals the one specified at step 13 (`c5`) |

**After**:
- Stop the child run first, then the parent run launched at step 15
