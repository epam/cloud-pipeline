# Validation of launch pipeline with timeout

Test verifies that a pipeline run stops automatically once the configured timeout elapses.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-376](EPMCMBIBPC-376.md) case |  |
| 2 | Click **Launch** button | The pipeline will be stopped after the number of minutes specified at step 4 of the [EPMCMBIBPC-376](EPMCMBIBPC-376.md) case; if the pipeline didn't finish by that time it will be stopped with **FAILED** status; the countdown starts from the beginning of the pipeline execution |
