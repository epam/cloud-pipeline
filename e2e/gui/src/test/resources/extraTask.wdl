workflow HelloWorld {
  call HelloWorld_print
  call MyTask
}

task HelloWorld_print {
  command {
    pipe_log SUCCESS "Running WDL pipeline" "Task1"
  }
}

task MyTask {
}