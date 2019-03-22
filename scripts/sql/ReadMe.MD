##SQL Scripts for Pipeline DB Deployment

#Create Pipelines
**create_pipeline.sql** creates entries for 4 pipelines in DB along with data storage rules. Provide the following arguments to run the script:
1. [&amp;1] Git repository URL for Demultiplex pipeline
2. [&amp;2] Git repository URL for Capture pipeline
3. [&amp;3] Git repository URL for Amplicon pipeline
4. [&amp;4] Git repository URL for Batch pipeline

#Create Data Storages
**create_datastorage.sql** creates entries for 4 major buckets in DB. Provide the following arguments to run the script:
1. [&amp;1] Name of the "Output" S3 bucket
2. [&amp;2] Name of the "Analysis" S3 bucket
3. [&amp;3] Name of the "Reference" S3 bucket
4. [&amp;4] Name of the "LIMS" S3 bucket

