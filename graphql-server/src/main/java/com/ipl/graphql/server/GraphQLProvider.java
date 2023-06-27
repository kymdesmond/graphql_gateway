package com.ipl.graphql.server;

import com.ipl.graphql.schema.OpenApiGraphQLSchemaBuilder;
import com.ipl.graphql.schema.SwaggerGraphQLSchemaBuilder;
import graphql.GraphQL;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class GraphQLProvider {

    private Map<String, Swagger> swaggerServices = new HashMap<>();
    private Map<String, OpenAPI> openApiServices = new HashMap<>();

    private final Environment environment;
    private GraphQL graphQL;

    public GraphQLProvider(Environment environment) {
        this.environment = environment;
    }

    /**
     * Registers a REST service
     * @param name
     * @param location
     */
    public void register(String name, String location) {
        switch (Objects.requireNonNull(environment.getProperty("schema"))) {
            case "openapi":
                openApiServices.put(name, new OpenAPIV3Parser().read(location));
                break;
            case "swagger":
                swaggerServices.put(name, new SwaggerParser().read(location));
                break;
            default:
                throw new IllegalArgumentException("Unknown schema type: " + environment.getProperty("schema"));
        }
        load();
    }

    /**
     * Registers a REST service
     * @param name
     */
    public void unregister(String name) {
        String schema = Objects.requireNonNull(environment.getProperty("schema"));
        if (schema.equals("openapi")) {
            openApiServices.remove(name);
        } else if (schema.equals("swagger")) {
            swaggerServices.remove(name);
        } else throw new IllegalArgumentException("Unknown schema type: " + environment.getProperty("schema"));
        load();
    }

    public Collection<String> services() {
        var schema = Objects.requireNonNull(environment.getProperty("schema"));
        if (schema.equals("openapi")) {
            return openApiServices.keySet();
        } else if (schema.equals("swagger")) {
            return swaggerServices.keySet();
        } else throw new IllegalArgumentException("Unknown schema type: " + environment.getProperty("schema"));
    }

    /**
     * Loads REST services in GraphQL schema
     */
    private void load() {
        var schema = Objects.requireNonNull(environment.getProperty("schema"));
        if (schema.equals("openapi")) {
            OpenApiGraphQLSchemaBuilder openapiGraphQLConverter = new OpenApiGraphQLSchemaBuilder();
            openApiServices.values().forEach(openapiGraphQLConverter::openapi);
            this.graphQL = GraphQL.newGraphQL(openapiGraphQLConverter.build()).build();
        } else if (schema.equals("swagger")) {
            SwaggerGraphQLSchemaBuilder swaggerGraphQLConverter = new SwaggerGraphQLSchemaBuilder();
            swaggerServices.values().forEach(swaggerGraphQLConverter::swagger);
            this.graphQL = GraphQL.newGraphQL(swaggerGraphQLConverter.build()).build();
        } else throw new IllegalArgumentException("Unknown schema type: " + environment.getProperty("schema"));
    }

    public GraphQL getGraphQL() {
        /**
         * TODO
         * Controls error when graphQL is null as there is no service registered
         */
        return graphQL;
    }
}
