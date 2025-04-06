package com.testleaf.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.testleaf.llm.LLMCompleteCodeGenerator;
import com.testleaf.llm.CodeGenerationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Slf4j
public class CodeGenerationController {

    private final LLMCompleteCodeGenerator llmCompleteCodeGenerator;

    /**
     * Generates complete test code (feature file, API class, POJOs, and step definitions)
     * from the provided parsed Swagger API details and test types.
     *
     * Example usage:
     * POST /api/generateCode
     * Body (raw JSON):
     * {
     *    "apiDetails": "Title: Accounts API\nVersion: 2.0.1\nDescription: API for ...\nServers: ...\nPaths: ...\nComponents/Schemas: ...",
     *    "testTypes": ["positive", "negative", "edge"]
     * }
     *
     * @param request Request payload containing API details and test types.
     * @return A JSON object with keys: featureFile, apiClass, pojos, stepDefinition.
     */
    @PostMapping("/generateCode")
    public ResponseEntity<CodeGenerationResponse> generateCode(@RequestBody CodeGenerationRequest request) {
        try {
            CodeGenerationResponse response = llmCompleteCodeGenerator.generateCompleteCode(
                    request.getApiDetails(), request.getTestTypes()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating code", e);
            return ResponseEntity.status(500).body(new CodeGenerationResponse("Error: " + e.getMessage()));
        }
    }

    /**
     * DTO representing the request payload.
     */
    public static class CodeGenerationRequest {
        private String apiDetails;
        private List<String> testTypes;

        public String getApiDetails() {
            return apiDetails;
        }
        public void setApiDetails(String apiDetails) {
            this.apiDetails = apiDetails;
        }
        public List<String> getTestTypes() {
            return testTypes;
        }
        public void setTestTypes(List<String> testTypes) {
            this.testTypes = testTypes;
        }
    }
}
