# Check instance on pipeline log page

Test verifies that a run's **Instance** section shows the disk, node type and cmd template matching the launched pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-100](EPMCMBIBPC-100.md) case |  |
| 2 | Click on the just-launched pipeline run |  |
| 3 | Click on the collapsed header **Instance** | <li> the collapsed header is expanded <li> the values near the labels **Disk**, **Node type**, **Cmd template** equal the same parameters of the pipeline created at the [EPMCMBIBPC-100](EPMCMBIBPC-100.md) case |
