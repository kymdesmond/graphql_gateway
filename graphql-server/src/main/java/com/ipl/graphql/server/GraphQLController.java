package com.ipl.graphql.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import graphql.ExecutionInput;
import graphql.ExecutionResult;

@RequestMapping("/graphql")
@RestController
@Slf4j
public class GraphQLController {
    private final GraphQLProvider graphQLProvider;

    public GraphQLController(GraphQLProvider graphQLProvider) {
        this.graphQLProvider = graphQLProvider;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> graphql(@RequestBody GraphQLRequestBody request) {
        log.info("graphql request -- {}", request);
        ExecutionResult result;
        if (request.getVariables() != null && request.getQuery() != null) {
            ExecutionInput in = ExecutionInput.newExecutionInput().query(request.getQuery()).variables(request.getVariables()).build();
            result = graphQLProvider.getGraphQL().execute(in);
        } else if (request.getMutation() != null) {
            ExecutionInput in = ExecutionInput.newExecutionInput().query(request.getMutation()).variables(request.getVariables()).build();
            result = graphQLProvider.getGraphQL().execute(in);
        } else {
            result = graphQLProvider.getGraphQL().execute(request.getQuery());
        }
        
        
        log.info("graphql response -- {}", result.isDataPresent() ? "success" : result.getErrors());
        log.debug("graphql full response -- {}", result);
        return ResponseEntity.ok(result);
    }

}
