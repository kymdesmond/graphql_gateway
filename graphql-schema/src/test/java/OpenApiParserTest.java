import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class OpenApiParserTest {

    @Test
    public void shouldParseOpenApiFromJson() {
        OpenAPI openAPI = new OpenAPIV3Parser().read("src/test/resources/listing-openapi.json");

        assertNotNull(openAPI);
    }
}
