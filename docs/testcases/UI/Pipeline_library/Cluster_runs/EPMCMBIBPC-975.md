# Validation of cluster's run

Test verifies launching a fixed-size cluster with 2 child nodes, and that both parent and child nodes finish successfully.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create/find a pipeline (from the SHELL/DEFAULT template) |  |
| 3 | Open the pipeline |  |
| 4 | Click the **RUN** button |  |
| 5 | On the appeared page click the **Exec environment** section |  |
| 6 | Input valid values in the **Node type**, **Disk (Gb)** fields |  |
| 7 | Click **Configure cluster** | The **Configure cluster** pop-up window appears |
| 8 | In the appeared pop-up window click **Cluster** |  |
| 9 | Input a natural number (`2`) into **Child nodes** |  |
| 10 | Click **OK** button |  |
| 11 | Click **Launch** button |  |
| 12 | Click **Launch** button in the appeared pop-up window | The **Runs** page opens and the just-launched pipeline with a **+** button near its name is displayed |
| 13 | Click the **+** button near the just-launched pipeline name on the **ACTIVE RUNS** tab | 2 child pipelines are displayed; the **Parent run** field shows the ID of the parent run |

**After**:
- All pipelines successfully finish
