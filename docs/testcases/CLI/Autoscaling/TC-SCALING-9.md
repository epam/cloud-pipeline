# Kill node during its using

**Actions**:

1. Launch a pipeline
2. Wait for a new node assigned to the pipeline launched at step 1
3. Terminate the node from step 2

***

**Expected result**:

Pipeline launched at step 1 is stopped with the status `STOPPED`
