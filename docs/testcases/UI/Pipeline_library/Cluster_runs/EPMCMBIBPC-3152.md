# Validation of Apache Spark cluster

Test verifies launching an Apache Spark cluster: the master/worker setup tasks and the Spark UI endpoint reporting the connected workers.

**Prerequisites**:
- Some of the functionality is supported by a specific deployment environment only

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Find the `rstudio` tool, click it |  |
| 4 | Hover over the **v** button near the **Run** button -> click **Custom settings** |  |
| 5 | Expand the **Exec environment** section on the appeared page |  |
| 6 | Select a node type with at least 32 Gb RAM |  |
| 7 | Click the **Configure cluster** control |  |
| 8 | Click the **Cluster** tab |  |
| 9 | Set the **Enable Apache Spark** checkbox |  |
| 10 | Click **OK** button | The name of the **Configure cluster** control becomes **Apache Spark Cluster (1 child node)** |
| 11 | Expand the **Advanced** section |  |
| 12 | Set the **Start idle** checkbox |  |
| 13 | Click **Launch** button |  |
| 14 | Click **Launch** button in the appeared pop-up window |  |
| 15 | Click the **+** button near the just-launched pipeline name at the **ACTIVE RUNS** tab |  |
| 16 | Save the RunIDs of the parent and the child pipeline runs |  |
| 17 | Click the just-launched pipeline name on the **ACTIVE RUNS** tab |  |
| 18 | Expand the **Parameters** section | Text appears that contains `CP_CAP_SPARK: true` |
| 19 | Wait until the **Endpoints** label appears |  |
| 20 | Click the **SparkMasterSetup** task | The log window contains the row `Spark master is started` |
| 21 | Click the **SparkWorkerSetup** task | The log window contains the row `Spark worker is started and connected to the master` |
| 22 | Click the **SparkMasterSetupWorkers** task | The log window contains the row `All workers are connected` |
| 23 | Click the **SparkUI** endpoint | A new page appears containing: <li> the header `Spark Master at spark://pipeline-<RunID>` (RunID is the parent run ID saved at step 16) <li> the label `Alive Workers: 2` <li> a table named `Workers (2)` with 2 rows for the 2 workers |

**After**:
- Stop the child run first, then the parent run launched at step 14
