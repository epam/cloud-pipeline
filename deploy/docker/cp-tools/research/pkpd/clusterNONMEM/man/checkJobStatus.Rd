\name{checkJobStatus}
\alias{checkJobStatus}
\title{
Receive details of a NONMEM job, performing model processing in a cluster
}
\description{
\code{checkJobStatus} is a wrapper, which calls SGE from R to retrieve details of
a job with a specific id. It returns an object with 2 fields:
- exitCode (exit code of the underlying system call. In a case of successful call 0 should be returned, 1 - otherwise)
- job (details for requested job, NULL in case no job was found)
}
\usage{
checkJobStatus(job_id)
}
\arguments{
  \item{job_id}{
  Id assigned to a job during submission to SGE cluster.
}
}
\author{
EPAM Systems
}
\examples{
\dontrun{
  # Status of a submitted job awaiting for resources
  > checkJobStatus(12)

    $exitCode
    [1] 0
    $job
    $job$id
    [1] "12"
    $job$state
    [1] "QUEUED"
    $job$name
    [1] "opt_nonmem_run_CONTROL5.hH2U9"
    $job$submissionTime
    [1] "Thu Aug 26 11:29:30 2021"
    $job$host
    [1] "clusterhost1"
    $job$usage
    NULL


  # Status of a running job
  > checkJobStatus(12)

    $exitCode
    [1] 0
    $job
    $job$id
    [1] "12"
    $job$state
    [1] "RUNNING"
    $job$name
    [1] "opt_nonmem_run_CONTROL5.hH2U9"
    $job$submissionTime
    [1] "Thu Aug 26 11:29:30 2021"
    $job$host
    [1] "clusterhost1"
    $job$usage
    [1] "1:            cpu=00:00:53, mem=0.47678 GB s, io=0.22297 GB, vmem=15.336M, maxvmem=56.754M

  # Status of a finished job
  > checkJobStatus(12)

    $exitCode
    [1] 0

    $job
    $job$id
    [1] "1"
    $job$state
    [1] "FINISHED"
    $job$name
    [1] "opt_nonmem_run_CONTROL5.hH2U9"
    $job$submissionTime
    [1] "Thu Aug 26 11:29:30 2021"
    $job$startTime
    [1] "Thu Aug 26 11:29:31 2021"
    $job$endTime
    [1] "Thu Aug 26 11:34:12 2021"
    $job$host
    [1] "clusterhost1"
    $job$slots
    [1] "1"
    $job$isFailed
    [1] FALSE
    $job$exitStatus
    [1] "0"


  # Status of a job, that doesn't exist
  > checkJobStatus(15)

    [1] "Unable to find a job!"
    $exitCode
    [1] 1

    $job
    NULL
}
}