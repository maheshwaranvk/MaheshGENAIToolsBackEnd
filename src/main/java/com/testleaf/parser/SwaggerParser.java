package com.testleaf.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class SwaggerParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String parseSwagger(String swaggerFilePath) {
        try {
            Path path = Paths.get(swaggerFilePath);
            String fileContent = new String(Files.readAllBytes(path));

            // Convert YAML to JSON if needed
            if (!fileContent.trim().startsWith("{")) {
                fileContent = convertYamlToJson(fileContent);
            }

            SwaggerParseResult result = new OpenAPIParser().readContents(fileContent, null, null);
            OpenAPI openAPI = result.getOpenAPI();

            if (openAPI == null) {
                System.err.println("⚠️ Failed to parse Swagger/OpenAPI file: " + result.getMessages());
                return null;
            }

            Map<String, Object> apiDetailsMap = buildApiDetailsMap(openAPI);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(apiDetailsMap);

        } catch (Exception e) {
            System.err.println("❌ Error while parsing Swagger file: " + e.getMessage());
            return null;
        }
    }

    private String convertYamlToJson(String yamlContent) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(yamlContent);
            return objectMapper.writeValueAsString(yamlMap);
        } catch (Exception e) {
            System.err.println("❌ Failed to convert YAML to JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Build API details in JSON-friendly format.
     */
    @SuppressWarnings("unchecked")
	private Map<String, Object> buildApiDetailsMap(OpenAPI openAPI) {
        Map<String, Object> apiDetails = new LinkedHashMap<>();

        // 1. Build apiDetails text block
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(openAPI.getInfo().getTitle()).append("\n");
        sb.append("Version: ").append(openAPI.getInfo().getVersion()).append("\n");
        sb.append("Description: ").append(openAPI.getInfo().getDescription()).append("\n\n");

        // 2. Servers
        sb.append("Servers:\n");
        openAPI.getServers().forEach(server -> sb.append("  - ").append(server.getUrl()).append("\n"));
        sb.append("\n");

        // 3. Paths and Methods
        openAPI.getPaths().forEach((path, pathItem) -> {
            sb.append("Path: ").append(path).append("\n");

            pathItem.readOperationsMap().forEach((method, operation) -> {
                sb.append("  Method: ").append(method.name()).append("\n");
                sb.append("    Summary: ").append(Optional.ofNullable(operation.getSummary()).orElse("No summary")).append("\n");
                sb.append("    OperationId: ").append(Optional.ofNullable(operation.getOperationId()).orElse("No operationId")).append("\n");

                if (operation.getRequestBody() != null) {
                    sb.append("    Request Body:\n");
                    sb.append("      Description: ").append(Optional.ofNullable(operation.getRequestBody().getDescription()).orElse("No description")).append("\n");
                    sb.append("      Content Types:\n");
                    operation.getRequestBody().getContent().forEach((contentType, mediaType) -> {
                        sb.append("        - ").append(contentType).append("\n");
                        sb.append("          Schema: ").append(schemaNameFromRef(mediaType.getSchema())).append("\n");
                    });
                }

                if (operation.getResponses() != null && !operation.getResponses().isEmpty()) {
                    sb.append("    Responses:\n");
                    operation.getResponses().forEach((code, response) -> {
                        sb.append("      - ").append(code).append(": ").append(response.getDescription()).append("\n");
                        if (response.getContent() != null) {
                            sb.append("        Content Types:\n");
                            response.getContent().forEach((contentType, mediaType) -> {
                                sb.append("          * ").append(contentType).append("\n");
                                sb.append("            Schema: ").append(schemaNameFromRef(mediaType.getSchema())).append("\n");
                            });
                        }
                    });
                }

                sb.append("\n");
            });
        });

        // 4. Components/Schemas
        sb.append("Components/Schemas:\n");
        openAPI.getComponents().getSchemas().forEach((name, schema) -> {
            sb.append("  - ").append(name).append(": type: ").append(schema.getType()).append("\n");
            if (schema.getProperties() != null) {
                sb.append("    properties:\n");
                schema.getProperties().forEach((propName, propSchema) -> {
                    sb.append("      ").append(propName).append(":\n");
                    sb.append("        type: ").append(((io.swagger.v3.oas.models.media.Schema<?>) propSchema).getType()).append("\n");
                    sb.append("        description: ").append(Optional.ofNullable(((io.swagger.v3.oas.models.media.Schema<?>) propSchema).getDescription()).orElse("No description")).append("\n");
                });
            }
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                sb.append("    required: ").append(schema.getRequired()).append("\n");
            }
            sb.append("\n");
        });

        apiDetails.put("apiDetails", sb.toString().replace("\n", "\\n"));
        apiDetails.put("testTypes", Collections.singletonList("Positive"));
        return apiDetails;
    }

    private String schemaNameFromRef(io.swagger.v3.oas.models.media.Schema<?> schema) {
        if (schema == null) return "No schema";
        if (schema.get$ref() != null) {
            return "$ref: '" + schema.get$ref() + "'";
        }
        return "inline-schema";
    }
}
