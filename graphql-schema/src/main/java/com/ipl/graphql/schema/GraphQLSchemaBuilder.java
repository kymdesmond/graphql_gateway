package com.ipl.graphql.schema;

import graphql.schema.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry;
import static graphql.schema.GraphQLObjectType.newObject;
import static graphql.schema.GraphQLSchema.newSchema;

@Slf4j
public class GraphQLSchemaBuilder {
    private static final String QUERY = "Query";
    private static final String MUTATION = "Mutation";

    /** Object types. */
    private Map<String, GraphQLObjectType> objectTypesMap = new HashMap<>();

    /** Input types. */
    private Map<String, GraphQLInputObjectType> inputObjectTypeMap = new HashMap<>();

    /** Interface types. */
    private Map<String, GraphQLInterfaceType> interfaceTypesMap = new HashMap<>();
    /** Query fields. */
    private Map<String, GraphQLFieldDefinition> queryFieldsMap = new HashMap<>();
    /** Mutation fields. */
    private Map<String, GraphQLFieldDefinition> mutationFieldsMap = new HashMap<>();
    /** Data fetchers. */
    private Map<FieldCoordinates, DataFetcher<?>> dataFetchersMap = new HashMap<>();
    /** Type resolvers. */
    private Map<String, TypeResolver> typeResolversMap = new HashMap<>();

    public GraphQLSchemaBuilder objectType(@NonNull GraphQLObjectType objectType) {
        if (!this.objectTypesMap.containsKey(objectType.getName())) {
            this.objectTypesMap.put(objectType.getName(), objectType);
        } else {
            log.warn("The object type '{}' has already been defined, its definition will be ignored", objectType.getName());
        }

        return this;
    }

    public GraphQLSchemaBuilder inputObjectType(@NonNull GraphQLInputObjectType inputObjectType) {
        if (!this.inputObjectTypeMap.containsKey(inputObjectType.getName())) {
            this.inputObjectTypeMap.put(inputObjectType.getName(), inputObjectType);
        } else {
            log.warn("The input object type '{}' has already been defined, its definition will be ignored", inputObjectType.getName());
        }

        return this;
    }

    public GraphQLSchemaBuilder objectTypes(@NonNull Collection<GraphQLObjectType> objectTypes) {
        objectTypes.forEach(this::objectType);

        return this;
    }

    public GraphQLSchemaBuilder inputObjectTypes(@NonNull Collection<GraphQLInputObjectType> inputObjectTypes) {
        inputObjectTypes.forEach(this::inputObjectType);

        return this;
    }

    public GraphQLSchemaBuilder interfaceType(@NonNull GraphQLInterfaceType interfaceType) {
        if (!this.interfaceTypesMap.containsKey(interfaceType.getName())) {
            this.interfaceTypesMap.put(interfaceType.getName(), interfaceType);
        } else {
            log.warn("The interface type '{}' has already been defined, its definition will be ignored",
                    interfaceType.getName());
        }

        return this;
    }

    public GraphQLSchemaBuilder interfaceTypes(@NonNull Collection<GraphQLInterfaceType> interfaceTypes) {
        interfaceTypes.forEach(this::interfaceType);

        return this;
    }

    public GraphQLSchemaBuilder queryField(@NonNull GraphQLFieldDefinition fieldDefinition) {
        if (!this.queryFieldsMap.containsKey(fieldDefinition.getName())) {
            this.queryFieldsMap.put(fieldDefinition.getName(), fieldDefinition);
        } else {
            log.warn("The query field '{}' has already been defined, its definition will be ignored", fieldDefinition.getName());
        }

        return this;
    }

    public GraphQLSchemaBuilder queryFields(@NonNull Collection<GraphQLFieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(this::queryField);

        return this;
    }

    public GraphQLSchemaBuilder mutationField(@NonNull GraphQLFieldDefinition fieldDefinition) {
        if (!this.mutationFieldsMap.containsKey(fieldDefinition.getName())) {
            this.mutationFieldsMap.put(fieldDefinition.getName(), fieldDefinition);
        } else {
            log.warn("The mutation field '{}' has already been defined, its definition will be ignored", fieldDefinition.getName());
        }

        return this;
    }

    public GraphQLSchemaBuilder mutationFields(@NonNull Collection<GraphQLFieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(this::mutationField);

        return this;
    }

    public GraphQLSchemaBuilder dataFetcher(@NonNull FieldCoordinates coordinates, @NonNull DataFetcher<?> dataFetcher) {
        if (!this.dataFetchersMap.containsKey(coordinates)) {
            this.dataFetchersMap.put(coordinates, dataFetcher);
        } else {
            log.warn("The data fetcher for '{}' has already been defined, its definition will be ignored", coordinates);
        }

        return this;
    }

    public GraphQLSchemaBuilder dataFetchers(Map<FieldCoordinates, DataFetcher<?>> dataFetchers) {
        if (dataFetchers!=null) {
            dataFetchers.forEach(this::dataFetcher);
        }

        return this;
    }

    public GraphQLSchemaBuilder typeResolver(@NonNull String typeName, @NonNull TypeResolver typeResolver) {
        if (!this.typeResolversMap.containsKey(typeName)) {
            this.typeResolversMap.put(typeName, typeResolver);
        } else {
            log.warn("The type resolver for '{}' has already been defined, its definition will be ignored",
                    typeName);
        }

        return this;
    }

    public GraphQLSchemaBuilder typeResolvers(Map<String, TypeResolver> typeResolvers) {
        if (typeResolvers!=null) {
            typeResolvers.entrySet().stream().forEach(entry -> this.typeResolver(entry.getKey(), entry.getValue()));
        }

        return this;
    }

    public GraphQLSchema build() {
        GraphQLSchema.Builder schemaBuilder = newSchema();

        // Interface types
        this.interfaceTypesMap.values().forEach(schemaBuilder::additionalType);

        // Object types
        this.objectTypesMap.values().forEach(schemaBuilder::additionalType);

        // Input types
        this.inputObjectTypeMap.values().forEach(schemaBuilder::additionalType);

        // Query
        GraphQLObjectType.Builder query = newObject().name(QUERY);
        this.queryFieldsMap.values().forEach(query::field);

        // Mutation
        GraphQLObjectType.Builder mutation = newObject().name(MUTATION);
        this.mutationFieldsMap.values().forEach(mutation::field);

        // Code registry
        GraphQLCodeRegistry.Builder codeRegistry = newCodeRegistry();
        this.dataFetchersMap.forEach(codeRegistry::dataFetcher);
        this.typeResolversMap.forEach(codeRegistry::typeResolver);
        schemaBuilder.codeRegistry(codeRegistry.build());

        return schemaBuilder
                .query(query.build())
                .mutation(mutation.build())
                .build();
    }
}