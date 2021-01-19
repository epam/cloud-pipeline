# Stop pipeline after the node creation but before its labeling

**Actions**:

1. Launch a pipeline
2. Wait for a new node starts the boot
3. Stop the pipeline launched at step 1
4. Check the label of the node from step 2
5. Launch a pipeline with the same instance type as at step 1
6. Wait the node is assigned to the pipeline launched at step 5

***

**Expected result**:

After step 4, the node has label with the pipeline run ID launched at step 1

After step 6:

- the node has label with the pipeline run ID launched at step 5
- the node used for the pipeline from step 5 is the same as was booted at step 2
