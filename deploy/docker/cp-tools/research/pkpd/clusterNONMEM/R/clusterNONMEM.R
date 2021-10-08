# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

runNonmemSge <- function(infile, outfile='', threads = 1, verbose = TRUE, blocking = FALSE) {
    exitCode <- 1
    taskDetails <- NULL
    if (!file.exists(infile)) {
        print(sprintf('No such file [%s] exists, exiting...', infile))
    } else {
        script <- system.file('shell/nonmem_submit.sh', package = 'clusterNONMEM')
        scriptArgs <- c(infile, threads, outfile)
        wrapperResult <- system2(script, args = scriptArgs, stdout = TRUE, stderr = TRUE)
        exitCode <- attr(wrapperResult, 'status')
        taskDetails <- NULL
        if (verbose) {
            print(wrapperResult)
        }
        if (is.null(exitCode)) {
            exitCode <- 0
            taskDetails <- .extractNonmemTaskDetailsFromSubmission(wrapperResult)
            if (blocking) {
                .waitJobFinish(taskDetails$id)
            }
        }
    }
    return(list('exitCode' = exitCode, 'details' = taskDetails))
}

checkJobStatus <- function(job_id) {
    return(suppressWarnings(.checkJobStatus(job_id)))
}

.waitJobFinish <- function(job_id) {
    prevState <- NULL
    while (TRUE) {
        sink('/dev/null')
        details <- checkJobStatus(job_id)
        sink()
        job <- details$job
        state <- job$state
        if (!is.null(prevState) && is.null(state)) {
            next
            Sys.sleep(5)
        }
        if (state == 'FINISHED') {
            break
        }
        prevState <- state
    }
}

.extractNonmemTaskDetailsFromSubmission <- function(submission_output) {
    return(list('file' = .extractInfoFromSubmissionLine(submission_output[1]),
                'slots' = .extractInfoFromSubmissionLine(submission_output[2]),
                'serviceDir' = .extractInfoFromSubmissionLine(submission_output[3]),
                'workdir' = .extractInfoFromSubmissionLine(submission_output[4]),
                'resultPath' = .extractInfoFromSubmissionLine(submission_output[5]),
                'id' = .extractJobIdFromSgeOutput(submission_output[6])))
}

.extractJobIdFromSgeOutput <- function(line) {
    match <- regexec('\\s\\d+\\s', line)
    if (length(match) < 0) {
        return(NULL)
    }
    return(trimws(regmatches(line, match[[1]])))
}

.extractInfoFromSubmissionLine <- function(line) {
    match <- regexec('\\[(.*)\\]', line)
    if (length(match) != 1) {
        return(NULL)
    }
    return(regmatches(line, match)[[1]][2])
}

.callSgeCommand <- function(cmd, job_id) {
    cmdArgs <- c('-j', job_id)
    res <- system2(cmd, args = cmdArgs, stdout = TRUE, stderr = TRUE)
    exitCode <- attr(res, 'status')
    return(list('exitCode' = exitCode, 'output' = res))
}

.findValueInOutput <- function(logs_list, prefix, colonPadding = 1) {
    result <- NULL
    for (i in 1:length(logs_list)) {
        log_line <- logs_list[i]
        if (startsWith(log_line, prefix = prefix)) {
            result <- trimws(substring(log_line, nchar(prefix) + 1 + colonPadding))
            break
        }
    }
    return(result)
}

.buildActiveJobDetails <- function(logs) {
    usageSummary <- .findValueInOutput(logs, 'usage')
    state <- 'QUEUED'
    if(!is.null(usageSummary)) {
        state <- 'RUNNING'
    }
    return(list('id' = .findValueInOutput(logs, 'job_number'),
                'state' = state,
                'name' = .findValueInOutput(logs, 'job_name'),
                'submissionTime' = .findValueInOutput(logs, 'submission_time'),
                'host' = .findValueInOutput(logs, 'sge_o_host'),
                'usage' = usageSummary))
}

.buildFinishedJobDetails <- function(logs) {
    return(list('id' = .findValueInOutput(logs, 'jobnumber', colonPadding = 0),
                'state' = 'FINISHED',
                'name' = .findValueInOutput(logs, 'jobname', colonPadding = 0),
                'submissionTime' = .findValueInOutput(logs, 'qsub_time', colonPadding = 0),
                'startTime' = .findValueInOutput(logs, 'start_time', colonPadding = 0),
                'endTime' = .findValueInOutput(logs, 'end_time', colonPadding = 0),
                'host' = .findValueInOutput(logs, 'hostname', colonPadding = 0),
                'slots' = .findValueInOutput(logs, 'slots', colonPadding = 0),
                'isFailed' = ('1' == .findValueInOutput(logs, 'failed', colonPadding = 0)),
                'exitStatus' = .findValueInOutput(logs, 'exit_status', colonPadding = 0)))
}

.checkJobStatus <- function(job_id) {
    exitCode <- 1
    job <- NULL
    res <- .callSgeCommand('qstat', job_id)
    if (is.null(res$exitCode)) {
        job <- .buildActiveJobDetails(res$output)
        exitCode <- 0
    } else {
        res <- .callSgeCommand('qacct', job_id)
        if (is.null(res$exitCode)) {
            job <- .buildFinishedJobDetails(res$output)
            exitCode <- 0
        } else {
            print('Unable to find a job!')
            exitCode <- 1
        }
    }
    return(list('exitCode' = exitCode, 'job' = job))
}
