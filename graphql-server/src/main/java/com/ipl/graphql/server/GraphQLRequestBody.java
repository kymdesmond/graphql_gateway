package com.ipl.graphql.server;

import lombok.Data;

import java.util.Map;
@Data
public class GraphQLRequestBody {
    private String query;
    private String mutation;
    private String operationName;
    private Map<String, Object> variables;
}
