# Edit and execute pipeline

Test verifies that a user granted all permissions on a pipeline can edit its files and launch it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-539](EPMCMBIBPC-539.md) case |  |
| 2 | Set the checkbox in the **Allow** column opposite all permissions |  |
| 3 | Log out |  |
| 4 | Login as the user specified at step 7 of the [EPMCMBIBPC-537](EPMCMBIBPC-537.md) case |  |
| 5 | Perform steps 6-13 of the [EPMCMBIBPC-556](EPMCMBIBPC-556.md) case | The file name is equal to the one specified at step 10 of the [EPMCMBIBPC-556](EPMCMBIBPC-556.md) case |
| 6 | Perform steps 6-17 of the [EPMCMBIBPC-557](EPMCMBIBPC-557.md) case | The code file content is equal to the one entered at step 12 of the [EPMCMBIBPC-557](EPMCMBIBPC-557.md) case |
| 7 | Perform steps 6-12 of the [EPMCMBIBPC-553](EPMCMBIBPC-553.md) case | <li>The launched pipeline appears on the **ACTIVE RUNS** tab of the **Runs** page <li>On the **Cluster state** page, a node appears containing a label with a run ID equal to the ID of the launched pipeline |
