# [MANUAL] Validation of node jobs tab

Test verifies the node's JOBS tab, both for a worker node running a pipeline and for the MASTER node.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Create a pipeline, open it |  |
| 3 | Click **RUN** button in the right upper corner |  |
| 4 | On the **Launch** page expand the **Advanced** section |  |
| 5 | Input into **Cmd template**: `sleep infinity` |  |
| 6 | Launch the pipeline |  |
| 7 | On the **Runs** page click on the just-launched pipeline (save the RunID) |  |
| 8 | Wait until the **SSH** hyperlink appears in the right upper corner |  |
| 9 | Open the **Cluster state** page |  |
| 10 | Click on the node that has a label with the RunID saved at step 7 |  |
| 11 | Click on **JOBS** tab | The node's **JOBS** tab appears containing: <li> **Name**, **Namespace**, **Status** columns <li> a jobs list containing the job `{pipeline_name}-{RunID}` (where `{pipeline_name}` is the pipeline name from step 2, `{RunID}` is the run ID saved at step 7), and e.g. `kube-flannel<...>`, `kube-proxy<...>` jobs <li> for the `{pipeline_name}-{RunID}` job: **Namespace** = `default`, **Status** = `Pending` or `Running` <li> for the `kube-flannel<...>` job: **Namespace** = `kube-system`, **Status** = `Pending` or `Running` |
| 12 | Click the plus button in front of the job `{pipeline_name}-{RunID}` | At least one row appears under the job containing the **CONTAINER** label with container name `pipeline` |
| 13 | Click the plus button in front of the job `kube-flannel<...>` | At least one row appears under the job containing the **CONTAINER** label with container name `kube-flannel` |
| 14 | Click the **return** button in the left upper corner |  |
| 15 | Click on the **MASTER** node |  |
| 16 | Repeat step 11 | The node's **JOBS** tab appears containing **Name**, **Namespace**, **Status**, **Requests**, **Limits**, **CPU**, **MEMORY** columns |
