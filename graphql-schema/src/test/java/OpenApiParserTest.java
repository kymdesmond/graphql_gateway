import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiParserTest {

    @Test
    void shouldParseOpenApiFromJson() {
        OpenAPI openAPI = new OpenAPIV3Parser().read("src/test/resources/listing-openapi.json");

        assertNotNull(openAPI);
    }
}
