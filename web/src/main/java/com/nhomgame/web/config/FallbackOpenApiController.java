package com.nhomgame.web.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackOpenApiController {

    private final OpenApiProperties props;

    public FallbackOpenApiController(OpenApiProperties props) {
        this.props = props;
    }

    @GetMapping(value = "/v3/api-docs/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> openapi() {
        Map<String, Object> root = new HashMap<>();
        root.put("openapi", "3.0.1");
        Map<String, Object> info = new HashMap<>();
        info.put("title", props.getTitle());
        info.put("version", props.getVersion());
        info.put("description", props.getDescription());
        root.put("info", info);
        root.put("paths", new HashMap<>());
        root.put("components", new HashMap<>());
        return root;
    }
}
