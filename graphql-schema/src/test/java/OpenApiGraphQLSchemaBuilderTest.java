import com.ipl.graphql.schema.OpenApiGraphQLSchemaBuilder;
import graphql.schema.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static graphql.Scalars.GraphQLID;
import static graphql.schema.FieldCoordinates.coordinates;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {OpenApiGraphQLSchemaBuilderTest.class, OpenApiParserTest.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class OpenApiGraphQLSchemaBuilderTest {

    private final static String OPENAPI_LOCATION = "src/test/resources/listing-openapi.json";
    @Test
    public void buildHasToDeclareQuery() {
        //given
        final OpenApiGraphQLSchemaBuilder builder = new OpenApiGraphQLSchemaBuilder();
        final OpenAPI openAPI = new OpenAPIV3Parser().read(OPENAPI_LOCATION);

        //when
        final GraphQLSchema graphQLSchema = builder.openapi(openAPI).build();

        //then
        assertNotNull(graphQLSchema.getQueryType());
    }

    @Test
    public void buildHasToDeclareMutation() {
        //given
        final OpenApiGraphQLSchemaBuilder builder = new OpenApiGraphQLSchemaBuilder();
        final OpenAPI openAPI = new OpenAPIV3Parser().read(OPENAPI_LOCATION);

        //when
        final GraphQLSchema graphQLSchema = builder.openapi(openAPI).build();

        //then
        assertNotNull(graphQLSchema.getMutationType());
    }

    @Test
    public void build_has_to_declare_Query_fields_by_OpenApi_path() {
        //given
        final OpenApiGraphQLSchemaBuilder builder = new OpenApiGraphQLSchemaBuilder();
        final OpenAPI openAPI = new OpenAPIV3Parser().read(OPENAPI_LOCATION);

        //when
        final GraphQLSchema graphQLSchema = builder.openapi(openAPI).build();
        final GraphQLFieldDefinition queryMakes = graphQLSchema.getQueryType().getFieldDefinition("getMakes");

        //then
        assertNotNull(queryMakes);
        assert(queryMakes.getType() instanceof GraphQLList);
        assertEquals("MakesEntityDto", ((GraphQLObjectType)((GraphQLList)queryMakes.getType()).getWrappedType()).getName());

        //and when
        final GraphQLFieldDefinition queryMake = graphQLSchema.getQueryType().getFieldDefinition("findMakeById");

        //then
        assertNotNull(queryMake);
        assert(queryMake.getType() instanceof GraphQLObjectType);
        assertEquals("MakesEntityDto", ((GraphQLObjectType)queryMake.getType()).getName());
        assertEquals(1, queryMake.getArguments().size());
        assertNotNull(queryMake.getArgument("id"));
        assertEquals(GraphQLID, queryMake.getArgument("id").getType());
    }

    @Test
    public void build_has_to_declare_Mutation_InputObject() {
        //given
        final OpenApiGraphQLSchemaBuilder builder = new OpenApiGraphQLSchemaBuilder();
        final OpenAPI openAPI = new OpenAPIV3Parser().read(OPENAPI_LOCATION);

        //when
        final GraphQLSchema graphQLSchema = builder.openapi(openAPI).build();
        final GraphQLFieldDefinition addMakeMutation = graphQLSchema.getMutationType().getFieldDefinition("addMake");

        //then
        assertNotNull(addMakeMutation);
        assertNotNull(addMakeMutation.getArgument("input"));
        assert(addMakeMutation.getArgument("input").getType() instanceof GraphQLInputObjectType);
    }


    @Test
    public void has_to_create_DataFetcher_By_Path() {
        //given
        final OpenApiGraphQLSchemaBuilder builder = new OpenApiGraphQLSchemaBuilder();
        final OpenAPI openAPI = new OpenAPIV3Parser().read(OPENAPI_LOCATION);

        //when
        final GraphQLSchema graphQLSchema = builder.openapi(openAPI).build();

        FieldCoordinates fieldCoordinates = coordinates("Query", "getMakes");
        DataFetcher<?> dataFetcher = graphQLSchema.getCodeRegistry().getDataFetcher(fieldCoordinates, graphQLSchema.getQueryType().getFieldDefinition("getMakes"));
        assertNotNull(dataFetcher);
    }
}
