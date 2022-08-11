package com.ipl.graphql.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/graphql")
@RestController
@Slf4j
public class GraphQLController {
    private final GraphQLProvider graphQLProvider;

    public GraphQLController(GraphQLProvider graphQLProvider) {
        this.graphQLProvider = graphQLProvider;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity graphql(@RequestBody GraphQLRequestBody request) {
        log.info("graphql request -- {}", request);
        Object result = null;
        if (request.getQuery() != null) {
            result = graphQLProvider.getGraphQL().execute(request.getQuery());
        } else if (request.getMutation() != null) {
            result = graphQLProvider.getGraphQL().execute(request.getMutation());
        }
        
        
        log.info("graphql response -- {}", result);
        return (result != null)? ResponseEntity.ok(result): ResponseEntity.noContent().build();
    }

}
