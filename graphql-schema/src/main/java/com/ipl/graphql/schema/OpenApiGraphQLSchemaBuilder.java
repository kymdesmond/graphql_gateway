package com.ipl.graphql.schema;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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
        List<GraphQLObjectType> objectTypes = null != openAPI.getComponents().getSchemas() ?
                openAPI.getComponents().getSchemas()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().endsWith("Dto"))
                .map(schemaEntry -> toGraphQLObjectType(schemaEntry.getKey(), schemaEntry.getValue()))
                .collect(Collectors.toList()) : Collections.emptyList();

        // input type definitions
        List<GraphQLInputObjectType> inputObjectTypes = null != openAPI.getComponents().getSchemas() ?
                openAPI.getComponents().getSchemas()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().endsWith("Request"))
                .map(schemaEntry -> toGraphQLInputObjectType(schemaEntry.getKey(), schemaEntry.getValue()))
                .collect(Collectors.toList()) : Collections.emptyList();

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
                                dataFetchers.put(FieldCoordinates.coordinates("Query", queryField.getName()), buildDataFetcher(host, key, value.getGet(), entry.getKey()));
                                return entry.getValue();
                            case POST:
                                log.info("{}: {}", entry.getKey(), entry.getValue());
                                 final GraphQLFieldDefinition postMutationField = pathToPostGraphQLField(entry.getValue().getOperationId(), value);
                                 mutationFields.add(postMutationField);
                                 dataFetchers.put(FieldCoordinates.coordinates("Mutation", postMutationField.getName()), buildDataFetcher(host, key, value.getPost(), entry.getKey()));
                                 return entry.getValue();
                            case PUT:
                                 log.info("{}: {}", entry.getKey(), entry.getValue());
                                 final GraphQLFieldDefinition putMutationField = pathToPutGraphQLField(entry.getValue().getOperationId(), value);
                                 mutationFields.add(putMutationField);
                                 dataFetchers.put(FieldCoordinates.coordinates("Mutation", putMutationField.getName()), buildDataFetcher(host, key, value.getPut(), entry.getKey()));
                                 return entry.getValue();
                            case DELETE:
                                log.info("{}: {}", entry.getKey(), entry.getValue());
                                final  GraphQLFieldDefinition deleteMutationField = pathToDeleteGraphQLField(entry.getValue().getOperationId(), value);
                                mutationFields.add(deleteMutationField);
                                dataFetchers.put(FieldCoordinates.coordinates("Mutation", deleteMutationField.getName()), buildDataFetcher(host, key, value.getDelete(), entry.getKey()));
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
                    .map(this::parameterToGraphQLArgument)
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
     * Maps Swagger path with GraphQLFieldDefinition
     * Put requests
     * @param pathItem
     * @return
     */
    private GraphQLFieldDefinition pathToPutGraphQLField(String name, PathItem pathItem) {
        log.info("Path to GraphQLFieldDefinition: {} -- {}", name, pathItem.toString());
        GraphQLArgument argument = newArgument()
                .name("input")
                .type(mapInputType("",
                        pathItem.getPut().getRequestBody().getContent().get("application/json").getSchema())
                        .orElse(null)).build();
        GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                .name(name)
                .argument(argument)
                .type(mapOutputType("",
                        pathItem.getPut().getResponses().get("200").getContent().get("application/json").getSchema())
                        .orElse(null)); // GraphQLString

        if (pathItem.getPut().getParameters() != null) {
            builder.arguments(pathItem.getPut().getParameters()
                    .stream()
                    .map(parameter -> parameterToGraphQLArgument(parameter))
                    .collect(Collectors.toList())
            );
        }
        return builder.build();
    }

    /**
     * Maps Swagger path with GraphQLFieldDefinition
     * Delete requests
     * @param pathItem
     * @return
     */
    private GraphQLFieldDefinition pathToDeleteGraphQLField(String name, PathItem pathItem) {
        log.info("Path to GraphQLFieldDefinition: {} -- {}", name, pathItem.toString());
        GraphQLFieldDefinition.Builder builder = newFieldDefinition()
                .name(name)
                .type(mapOutputType("",
                        pathItem.getDelete().getResponses().get("200").getContent().get("application/json").getSchema())
                        .orElse(null)); // GraphQLString

        if (pathItem.getDelete().getParameters() != null) {
            builder.arguments(pathItem.getDelete().getParameters()
                    .stream()
                    .map(parameter -> parameterToGraphQLArgument(parameter))
                    .collect(Collectors.toList())
            );
        }
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
        String swaggerType = parameter.getSchema().getType();
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
        return type.map(graphQLOutputType -> newFieldDefinition()
                .name(property.getKey())
                .type(graphQLOutputType)
                .build());
    }

    private Optional<GraphQLInputObjectField> propertyToGraphQLInputObjectField(Map.Entry<String, Schema> property) {
        Optional<GraphQLInputType> type = mapInputType(property.getKey(), property.getValue());
        return type.map(graphQLInputType -> newInputObjectField()
                .name(property.getKey())
                .type(graphQLInputType)
                .build());
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
    private DataFetcher buildDataFetcher(String host, String path, Operation operation, PathItem.HttpMethod httpMethod) {
        final OkHttpClient client = new OkHttpClient();
        final ObjectMapper objectMapper = new ObjectMapper();
        final String url = host + path;
        log.info("fetch data from host -- {}", host);
        log.info("fetch data from url -- {}", url);
        List<String> pathParams = Optional.ofNullable(operation.getParameters()).orElse(Collections.emptyList())
                .stream()
                .filter(parameter -> parameter.getIn().equals("path"))
                .map(Parameter::getName)
                .collect(Collectors.toList());
        List<String> queryParams = Optional.ofNullable(operation.getParameters()).orElse(Collections.emptyList())
                .stream()
                .filter(parameter -> parameter.getIn().equals("query"))
                .map(Parameter::getName)
                .collect(Collectors.toList());

        return dataFetchingEnvironment -> {
            String urlParams = pathParams
                    .stream()
                    .reduce(url, (acc, curr) -> url.replaceAll(String.format("\\{%s}", curr), dataFetchingEnvironment.getArgument(curr).toString()));
            okhttp3.RequestBody requestBody = okhttp3.RequestBody
                    .create(MediaType.parse("application/json; charset=utf-8"), objectMapper.writeValueAsString(dataFetchingEnvironment.getArgument("input")));
            Map<String, String> queryParamMap = new HashMap<>();
            queryParams.forEach(queryParam -> queryParamMap.put(queryParam, dataFetchingEnvironment.getArgument(queryParam).toString()));
            String urlString = formatUrlWithQueryParameters(urlParams, queryParamMap);
            Request request;
            Headers headers = new Headers.Builder()
                    .add("TraceId", dataFetchingEnvironment.getExecutionId().toString())
                    .build();
            switch (httpMethod) {
                case POST:
                    request = new Request.Builder()
                            .headers(headers)
                            .url(urlString)
                            .post(requestBody)
                            .build();
                    break;
                case PUT:
                    request = new Request.Builder()
                            .headers(headers)
                            .url(urlString)
                            .put(requestBody)
                            .build();
                    break;
                case DELETE:
                    request = new Request.Builder()
                            .headers(headers)
                            .url(urlString)
                            .delete()
                            .build();
                    break;
                default:
                    request = new Request.Builder()
                            .headers(headers)
                            .url(urlString)
                            .build();
                    break;
            }
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                final String json = response.body().string();
                return objectMapper.readValue(json, new TypeReference<>(){});
            }
            return null;
        };

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
    
     private String formatUrlWithQueryParameters(String baseUrl, Map<String, String> queryParams) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);

        if (!queryParams.isEmpty()) {
            urlBuilder.append('?');
            
            try {
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    String key = URLEncoder.encode(entry.getKey(), "UTF-8");
                    String value = URLEncoder.encode(entry.getValue(), "UTF-8");
                    urlBuilder.append(key).append('=').append(value).append('&');
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            
            // Remove the last '&' character
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        return urlBuilder.toString();
    }
}
