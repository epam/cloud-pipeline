\name{runNonmem}
\alias{runNonmem}
\title{
Run NONMEM model processing in a cluster
}
\description{
\code{nonmemSubmit} starts from R NONMEM model processing for
  a control stream file passed as \code{infile} argument.
  Function is intended to submit processing as an SGE job.
  Returns `exitCode` of underlying `qsub` call and `details` object
  with the summary describing submission: 'file', 'slots', 'serviceDir', 'workdir', 'resultPath', 'id'
}
\usage{
nonmemSubmit(infile, outfile='', threads = 1, verbose = TRUE, blocking = FALSE)
}
\arguments{
  \item{infile}{
  Mandatory path to control stream file. File must exist, name without
  extension is treated as a model name for all further processing.
}
  \item{outfile}{
  Optional path to the file, which should store the processing results.
   If a relative path is specified, the file will be created next to infile.
   If it is not passed, the result will be stored in a 'service' directory as 'results.txt'.
}
  \item{threads}{
  Optional number of threads intended to be used during processing.
  This number is used to build suitable PARAFILE and will be specified
  as a slots requirement for a cluster job. Defaults to 1.
}
\item{verbose}{
  Defines, whether to print info about job submission or not. Defaults to TRUE
}
\item{blocking}{
  Allows to perform modeling in a synchronous way - if set to TRUE,
  the method call will be blocked till the job reaches 'FINISHED' state. Defaults to FALSE
}
}
\author{
EPAM Systems
}
\examples{
\dontrun{

  # Simple execution with all defaults for model 'example'
  > nonmemSubmit('/common/models/example1/example.ctl')
    [1] "Processing [/common/models/example1/example.ctl] with a NONMEM on a cluster"
    [2] "Using [1] slots"
    [3] "Service dir: [/common/nonmem-sge/common_models_example1_example.hH2U9]"
    [4] "Workdir: [/common/models/example1/]"
    [5] "Results will be stored in [/common/nonmem-sge/common_models_example1_example.hH2U9/results.txt]"
    [6] "Your job 1 (\"common_models_example1_example.hH2U9\") has been submitted"

    $exitCode
    0

    $details
    $details$file
    [1] "/common/models/example1/example.ctl"
    $details$slots
    [1] "1"
    $details$serviceDir
    [1] "/common/nonmem-sge/common_models_example1_example.hH2U9"
    $details$workdir
    [1] "/common/models/example1"
    $details$resultPath
    [1] "/common/nonmem-sge/common_models_example1_example.hH2U9/results.txt"
    $details$id
    [1] "1"


  # Processing of a model using multiple threads
  > nonmemSubmit('/common/models/example.ctl', threads = 4, verbose = FALSE)
    $exitCode
    0

    $details
    $details$file
    [1] "/common/models/example1/example.ctl"
    $details$slots
    [1] "4"
    $details$serviceDir
    [1] "/common/nonmem-sge/common_models_example1_example.xDer4"
    $details$workdir
    [1] "/common/models/example1"
    $details$resultPath
    [1] "/common/nonmem-sge/common_models_example1_example.xDer4/results.txt"
    $details$id
    [1] "2"


  # Specifying output file to store processing results in the same directory as \code{infile}.
  > nonmemSubmit('/common/models/example.ctl', outfile = 'output.txt', threads = 4, verbose = FALSE)
    $exitCode
    0

    $details
    $details$file
    [1] "/common/models/example1/example.ctl"
    $details$slots
    [1] "4"
    $details$serviceDir
    [1] "/common/nonmem-sge/common_models_example1_example.dEt7Y"
    $details$workdir
    [1] "/common/models/example1"
    $details$resultPath
    [1] "/common/models/example1/output.txt"
    $details$id
    [1] "3"


  # Passing absolute output file path to store processing results in some specific place.
  > nonmemSubmit('/common/models/example.ctl', outfile = '/common/nonmem-results/example-output.txt',
                 threads = 2, verbose = FALSE)
    $exitCode
    0

    $details
    $details$file
    [1] "/common/models/example1/example.ctl"
    $details$slots
    [1] "2"
    $details$serviceDir
    [1] "/common/nonmem-sge/common_models_example1_example.QyPYm"
    $details$workdir
    [1] "/common/models/example1"
    $details$resultPath
    [1] "/common/nonmem-results/example-output.txt"
    $details$id
    [1] "4"

  # Try to submit nonexistent input file.
  > nonmemSubmit('/common/models/nonexistent.ctl')
    [1] "No such file [/common/models/nonexistent.ctl] exists, exiting..."
    $exitCode
    [1] 1

    $job
    NULL

}
}