# Launch and check launch parameters in pipeline logs page

Test verifies that launching from a configuration uses that configuration's node type and finishes quickly.

**Preparations**:
1. Perform the [EPMCMBIBPC-800](EPMCMBIBPC-800.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Launch** button |  |
| 2 | Click **Launch** button in the appeared pop-up |  |
| 3 | On the **ACTIVE RUNS** page click on the just-launched pipeline |  |
| 4 | Click on the collapsed header **Instance** | <li> the **Node type** value equals the one set at the [EPMCMBIBPC-800](EPMCMBIBPC-800.md) case <li> the pipeline finishes ~1 minute after the node is initialized |
