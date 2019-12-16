# API tutorials - Usage scenario

This sections provides a number of implementations of the data transfer/processing automation via the Cloud Pipeline API capabilities.

We'll use a quite common usage scenario to implement the automation via different approaches: process a local [10xGenomics](https://www.10xgenomics.com/) dataset (e.g. produced by the on-prem machinery) in the Cloud Pipeline compute environment.

The scenario for the automation consists of the following steps:
* Upload a dataset (directory with the FASTQ files to the S3 bucket)
* Run the dataset processing using the cellranger count command
* Download the data processing results back to a local filesystem

The subsequent sections provide implementation examples in a number of languages:

* [`pipe` CLI implementation](Automation_via_CLI.md)
* [Direct HTTP calls via `curl`](Direct_HTTP_API.md)
* [JavaScript implementation](JavaScript_example.md)
