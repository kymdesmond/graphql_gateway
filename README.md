# Graphql Gateway
## A GraphQL gateway for your API
### Architecture
#### Gateway server
Exposes the endpoint **POST /graphql** for query requests
```graphql
query {
  usersById(id: "1") {
    name
    email
  }
}
```
```graphql
type UserDto {
  id: ID
  name: String
  email: String
}
```
#### GraphQL schema
Converts open api to graphql schema
#### GraphQL gateway server
Springboot server that exposes the following endpoints:
- **POST /graphql** for query and mutation requests
- **GET /registry** to list registered services in the gateway
- **DELETE /registry** to unregister services from the gateway
- **POST /registry** to register services in the gateway
#### GraphQL registry
Exposes the following endpoints to manage registration of services:

**POST /registry** to register services in the gateway
```shell
curl --location --request POST 'http://localhost:8080/registry' \
--header 'Content-Type: application/json' \
--data-raw '{
"name": "UsersService",
"url": "http://localhost:8082/v3/api-docs"
}'
```

**DELETE /registry** to unregister services from the gateway
```shell
curl --location --request DELETE 'http://localhost:8080/registry?service=UsersService' \
--header 'Content-Type: application/json' \
--data-raw '{
"name": "UsersService",
"url": "http://localhost:8082/v3/api-docs"
}'
```
#### GraphQL registry client
### Service Development Guidelines
This Outlines the recommended development guidelines for services to be registered with the GraphQL gateway.
- Use open api to describe your service
```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-ui</artifactId>
  <version>1.6.9</version>
</dependency>
```
- Naming conventions:
  - Service names should be in PascalCase.
  - Endpoint names should be in camelCase.
  - Operation names should be in camelCase and descriptive.
  - Field names should be in camelCase.
  
```java
@RestController("/users")
public class UsersController {
  @GetMapping("")
  public List<UserDto> getUsers() {
      return new ArrayList<>();
  }
  @GetMapping("/{id}")
  public UserDto getUserById(@PathVariable("id") String id) {
    return new UserDto();
  }
  
  @PostMapping("/create")
  public UserDto createUser(@RequestBody UserDto user) {
      return new UserDto();
  }
    
  @PutMapping("/update/{id}")
  public UserDto updateUser(@PathVariable("id") String id, @RequestBody UserDto user) {
      return new UserDto();
  }
  
  @DeleteMapping("/delete/{id}")
  public UserDto deleteUser(@PathVariable("id") String id) {
      return new UserDto();
  }
}
```