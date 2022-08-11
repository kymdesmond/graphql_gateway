package com.ipl.graphql.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.*;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.*;
import java.util.stream.Collectors;

import static graphql.Scalars.*;
import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLInputObjectField.newInputObjectField;
import static graphql.schema.GraphQLInputObjectType.newInputObject;
import static graphql.schema.GraphQLObjectType.newObject;

@Slf4j
public class OpenApiGraphQLSchemaBuilder {
    
    private final GraphQLSchemaBuilder schemaBuilder;

    private final Map<String, GraphQLScalarType> scalarTypes = new HashMap<>() {
        {put("string", GraphQLString);}
        {put("integer", GraphQLInt);}
        {put("boolean", GraphQLBoolean);}
        {put("double", GraphQLFloat);}
    };

    public OpenApiGraphQLSchemaBuilder() {
        this.schemaBuilder = new GraphQLSchemaBuilder();
    }
 
    public OpenApiGraphQLSchemaBuilder openapi(OpenAPI openAPI) {
        log.info("--Building GraphQL schema from OpenAPI--");
        // type definitions
        List<GraphQLObjectType> objectTypes = openAPI.getComponents().getSchemas()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().endsWith("Dto"))
                .map(schemaEntry -> toGraphQLObjectType(schemaEntry.getKey(), schemaEntry.getValue()))
                .collect(Collectors.toList());

        // input type definitions
        List<GraphQLInputObjectType> inputObjectTypes = openAPI.getComponents().getSchemas()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().endsWith("Request"))
                .map(schemaEntry -> toGraphQLInputObjectType(schemaEntry.getKey(), schemaEntry.getValue()))
                .collect(Collectors.toList());

        // query type
        List<GraphQLFieldDefinition> queryFields = new ArrayList<>();

        // data fetchers
        Map<FieldCoordinates, DataFetcher<?>> dataFetchers = new HashMap<>();

        // mutation type
        List<GraphQLFieldDefinition> mutationFields = new ArrayList<>();

        // subscription type
        List<GraphQLFieldDefinition> subscriptionFields = new ArrayList<>();

        String host = openAPI.getServers().get(0).getUrl();
        // recommended to use get and post for now
        openAPI.getPaths().forEach((key, value) -> {
            Map<PathItem.HttpMethod, Operation> operationsMap = value.readOperationsMap();
            operationsMap.entrySet().stream()
                    .map(entry -> {
                        switch (entry.getKey()) {
                            case GET:
                                log.info("GET: {}", entry.getValue());
                                final GraphQLFieldDefinition queryField = pathToGraphQLField(entry.getValue().getOperationId(), value);
                                queryFields.add(queryField);
                                dataFetchers.put(FieldCoordinates.coordinates("Query", queryField.getName()), buildDataFetcher(host, key, value.getGet(), "GET"));
                                return entry.getValue();
                            case POST:
                                log.info("POST: {}", entry.getValue());
                                 final GraphQLFieldDefinition mutationField = pathToPostGraphQLField(entry.getValue().getOperationId(), value);
                                 mutationFields.add(mutationField);
                                 dataFetchers.put(FieldCoordinates.coordinates("Mutation", mutationField.getName()), buildDataFetcher(host, key, value.getPost(), "POST"));
                                 return entry.getValue();
                            case PUT:
                                log.info("PUT: {}", entry.getValue());
                                return entry.getValue();
                            case DELETE:
                                log.info("DELETE: {}", entry.getValue());
                                return entry.getValue();
                            case PATCH:
                                log.info("PATCH: {}", entry.getValue());
                                return entry.getValue();

                            default:
                                return null;
                        }
                    })
                    .collect(Collectors.toList());
        });

        schemaBuilder
                .queryFields(queryFields)
                .objectTypes(objectTypes)
                .mutationFields(mutationFields)
                .inputObjectTypes(inputObjectTypes)
                .dataFetchers(dataFetchers);

        return this;
    }

    public GraphQLSchema build() {
        log.info("--GraphQL schema ready--");
        return schemaBuilder.build();
    }

    /**
     * Maps Swagger path with GraphQLFieldDefinition
     * Get requests
     * @param pathItem
     * @return
     */
    private GraphQLFieldDefinition pathToGraphQLField(String name, PathItem pathItem) {
        log.info("Path to GraphQLFieldDefinition: {} -- {}", name, pathItem.toString());
        GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                .name(name)
                .type(mapOutputType("",
                        pathItem.getGet().getResponses().get("200").getContent().get("application/json").getSchema())
                        .orElse(null)); // GraphQLString

        if (pathItem.getGet().getParameters() != null) {
            builder.arguments(pathItem.getGet().getParameters()
                    .stream()
                    .map(parameter -> parameterToGraphQLArgument(parameter))
                    .collect(Collectors.toList())
            );
        }

        return builder.build();
    }

    /**
     * Maps Swagger path with GraphQLFieldDefinition
     * Post requests
     * @param pathItem
     * @return
     */
    private GraphQLFieldDefinition pathToPostGraphQLField(String name, PathItem pathItem) {
        log.info("Path to GraphQLFieldDefinition: {} -- {}", name, pathItem.toString());
        GraphQLArgument argument = newArgument()
                .name("input")
                .type(mapInputType("",
                        pathItem.getPost().getRequestBody().getContent().get("application/json").getSchema())
                        .orElse(null)).build();
        GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                .name(name)
                .argument(argument)
                .type(mapOutputType("",
                        pathItem.getPost().getResponses().get("200").getContent().get("application/json").getSchema())
                        .orElse(null)); // GraphQLString

        return builder.build();
    }

    /**
     * Maps Swagger parameter with GraphQLArgument
     * @param parameter
     * @return
     */
    private GraphQLArgument parameterToGraphQLArgument(Parameter parameter) {
        return newArgument()
                .name(parameter.getName())
                .type(mapInputType(parameter))
                .build();
    }

    /**
     * Maps swagger parameter to graphql type
     *
     * @param parameter
     * @return
     */
    private GraphQLInputType mapInputType(Parameter parameter) {
        final String fieldName = parameter.getName();
        String swaggerType = null;
        if (parameter instanceof PathParameter) {
            swaggerType = parameter.getSchema().getType();
        } else if (parameter instanceof RequestBody) {
            swaggerType = parameter.getSchema().getType();
        }
        if (isID(fieldName)) {
            return GraphQLID;
        } else {
            return scalarTypes.get(swaggerType);
        }
    }

    private GraphQLObjectType toGraphQLObjectType(String name, Schema schemaModel) {
        Map<String, Schema> properties = schemaModel.getProperties();
        List<GraphQLFieldDefinition> fields = properties.entrySet()
                .stream()
                .map(property -> propertyToGraphQLField(property))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return newObject()
                .name(name)
                .fields(fields)
                .build();
    }

    private GraphQLInputObjectType toGraphQLInputObjectType(String name, Schema schemaModel) {
        Map<String, Schema> properties = schemaModel.getProperties();
        List<GraphQLInputObjectField> fields = properties.entrySet()
                .stream()
                .map(property -> propertyToGraphQLInputObjectField(property))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return newInputObject()
                .name(name)
                .fields(fields)
                .build();
    }

    private Optional<GraphQLFieldDefinition> propertyToGraphQLField(Map.Entry<String, Schema> property) {
        Optional<GraphQLOutputType> type = mapOutputType(property.getKey(), property.getValue());
        if (!type.isPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(newFieldDefinition()
                    .name(property.getKey())
                    .type(type.get())
                    .build()
            );
        }
    }

    private Optional<GraphQLInputObjectField> propertyToGraphQLInputObjectField(Map.Entry<String, Schema> property) {
        Optional<GraphQLInputType> type = mapInputType(property.getKey(), property.getValue());
        if (!type.isPresent()) {
            return Optional.empty();
        } else {
            return Optional.of(newInputObjectField()
                    .name(property.getKey())
                    .type(type.get())
                    .build()
            );
        }
    }

    private Optional<GraphQLOutputType> mapOutputType(String name, Schema schema) {
        GraphQLOutputType outputType = null;

        if (isID(name)) {
            log.info("{} is ID", name);
            outputType = GraphQLID;
        } else if (scalarTypes.containsKey(schema.getType())) {
            outputType = scalarTypes.get(schema.getType());
            log.info("{} is scalar type: {}", name, outputType);
        } else if (isReference(schema)) {
            outputType = GraphQLTypeReference.typeRef(schema.get$ref().replace("#/components/schemas/", ""));
            log.info("{} is reference type: {}", name, outputType);
        } else if (isArray(schema)) {
            outputType = GraphQLList.list(mapOutputType(name, ((ArraySchema)schema).getItems()).orElse(null));
            log.info("{} is array type {}", name, outputType);
        }
        return Optional.ofNullable(outputType);
    }

    private Optional<GraphQLInputType> mapInputType(String name, Schema schema) {
        GraphQLInputType inputType = null;

        if (isID(name)) {
            log.info("{} is ID", name);
            inputType = GraphQLID;
        } else if (scalarTypes.containsKey(schema.getType())) {
            inputType = scalarTypes.get(schema.getType());
            log.info("{} is scalar type: {}", name, inputType);
        } else if (isReference(schema)) {
            inputType = GraphQLTypeReference.typeRef(schema.get$ref().replace("#/components/schemas/", ""));
            log.info("{} is reference type: {}", name, inputType);
        } else if (isArray(schema)) {
            inputType = GraphQLList.list(mapInputType(name, ((ArraySchema)schema).getItems()).orElse(null));
            log.info("{} is array type {}", name, inputType);
        }
        return Optional.ofNullable(inputType);
    }

    /**
     * Builds DataFetcher for a given query field
     * @return
     */
    private DataFetcher buildDataFetcher(String host, String path, Operation operation, String operationName) {
        final OkHttpClient client = new OkHttpClient();
        final ObjectMapper objectMapper = new ObjectMapper();
        final String url = host + path;
        log.info("fetch data from host -- {}", host);
        log.info("fetch data from url -- {}", url);
        List<String> pathParams = Optional.ofNullable(operation.getParameters()).orElse(Collections.emptyList())
                .stream()
                .map(Parameter::getName)
                .collect(Collectors.toList());

        if (operationName.equalsIgnoreCase("GET")) {
            return dataFetchingEnvironment -> {
                String urlParams = pathParams
                        .stream()
                        .reduce(url, (acc, curr) -> url.replaceAll(String.format("\\{%s}", curr), dataFetchingEnvironment.getArgument(curr).toString()));
                Request request = new Request.Builder().url(urlParams).build();
                Response response = client.newCall(request).execute();
                final String json = response.body().string();

                return objectMapper.readValue(json, new TypeReference<>(){});
            };
        } else {
            return dataFetchingEnvironment -> {
                String urlParams = pathParams
                        .stream()
                        .reduce(url, (acc, curr) -> url.replaceAll(String.format("\\{%s}", curr), dataFetchingEnvironment.getArgument(curr).toString()));
                Request request = new Request.Builder()
                        .url(urlParams)
                        .post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json; charset=utf-8"), objectMapper.writeValueAsString(dataFetchingEnvironment.getArgument("input"))))
                        .build();
                Response response = client.newCall(request).execute();
                final String json = response.body().string();
                return objectMapper.readValue(json, new TypeReference<>(){});
            };
        }
    }


    /**
     * Returns true if swagger property is Type reference
     * @param swaggerSchema
     * @return
     */
    private boolean isReference(Schema swaggerSchema) {
        return swaggerSchema.get$ref() != null && swaggerSchema.get$ref().contains("#/components/schemas/");
    }

    /**
     * Returns true if swagger property is Array of types
     * @param swaggerSchema
     * @return
     */
    private boolean isArray(Schema swaggerSchema) {
        return swaggerSchema instanceof ArraySchema;
    }


    /**
     * Returns true if the fieldName refers to the ID field
     * @param fieldName
     * @return
     */
    private boolean isID(String fieldName) {
        return (fieldName.equals("id"));
    }

    private String pathToType(String path) {
        log.info("Path to type: path -- {}", path);
        String type = Arrays.stream(path.split("/"))
                .reduce("", (acc, curr) -> (acc.isBlank())?curr: acc + buildPathName(curr));
        log.info("Path to type: type -- {}", type);
        return type.replaceAll("-", ""); // TODO: 15/07/22 convert to camel case
    }

    private String buildPathName(String name) {
        /**
         * Builds graphql type name from swagger path
         * /books -> books
         * /books/{id} -> booksById
         * /books/library/{id} -> booksWithLibraryById
         */
        final String PARAM_FORMAT = "By%s";
        final String PATH_FORMAT = "With%s";
        return isParam(name)?String.format(PARAM_FORMAT, capitalize(name.replaceAll("[{}]", ""))): String.format(PATH_FORMAT, capitalize(name));
    }

    private boolean isParam(@NonNull String name) {
        return (name.indexOf("{") == 0 && (name.lastIndexOf("}") == (name.length()-1))) ;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
