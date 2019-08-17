package com.epam.pipeline.tesadapter.service;

import com.epam.pipeline.tesadapter.entity.TesCancelTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesCreateTaskResponse;
import com.epam.pipeline.tesadapter.entity.TesTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TesTaskService {
    @Autowired
    private ObjectMapper objectMapper;

    public ResponseEntity<TesCreateTaskResponse> submitTesTask(TesTask body) {
        try {
            return new ResponseEntity<TesCreateTaskResponse>(objectMapper.readValue("STUBBED",
                    TesCreateTaskResponse.class), HttpStatus.NOT_IMPLEMENTED);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<TesCreateTaskResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<TesTask> getTesTask(String id) {
        try {
            return new ResponseEntity<TesTask>(objectMapper.readValue("STUBBED", TesTask.class),
                    HttpStatus.NOT_IMPLEMENTED);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<TesTask>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<TesCancelTaskResponse> cancelTesTask(String id) {
        try {
            return new ResponseEntity<TesCancelTaskResponse>(objectMapper.readValue("",
                    TesCancelTaskResponse.class), HttpStatus.NOT_IMPLEMENTED);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<TesCancelTaskResponse>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
