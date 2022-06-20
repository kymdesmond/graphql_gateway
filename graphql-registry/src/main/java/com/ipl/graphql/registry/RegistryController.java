package com.ipl.graphql.registry;


import com.ipl.graphql.server.GraphQLProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/registry")
@RestController
public class RegistryController {
    private final GraphQLProvider graphQLProvider;

    public RegistryController(GraphQLProvider graphQLProvider) {
        this.graphQLProvider = graphQLProvider;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity list() {
        return ResponseEntity.ok(graphQLProvider.services());
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity register(@RequestBody ServiceDto serviceDto) {
        graphQLProvider.register(serviceDto.getName(), serviceDto.getUrl());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity unregister(@RequestParam("service") String name) {
        graphQLProvider.unregister(name);
        return ResponseEntity.noContent().build();
    }
}
