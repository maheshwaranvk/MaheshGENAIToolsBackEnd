package com.testleaf.controller;

import com.testleaf.llm.CodeGenerationResponse;
import com.testleaf.llm.FeatureFileGenerator;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
@Slf4j
public class SpiraTestCaseExtractController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FeatureFileGenerator featureFileGenerator;

    @Value("${spira.api.url}")
    private String spiraBaseUrl;

    @Value("${spira.api.username}")
    private String spiraUsername;

    @Value("${spira.api.key}")
    private String spiraApiKey;

    @Value("${spira.project.id}")
    private int spiraProjectId;

    @PostMapping("/generateFeatureFile")
    public ResponseEntity<String> getTestCaseSteps(@RequestBody FeatureFileGenerationRequest request) {
        String baseUrl = String.format(
                "%s/projects/%d/test-cases/%s",
                spiraBaseUrl,
                spiraProjectId,
                request.getTestcaseId()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic bWFoZXNod2FyYW46ezVDRDNFNjczLTk1MDAtNEQxQS1COTQxLTg0Njg2QThEQjlGMn0=");
        headers.set("Accept", "application/json"); // Recommended
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("username", spiraUsername)
                .queryParam("api-key", "{" + spiraApiKey + "}") // Keep curly braces
                .build()
                .toUri();
    System.out.println(url);
        try {
            //ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,  // Now includes Authorization header
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Spira API returned non-success status: {}", response.getStatusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to fetch test case from Spira");
            }

            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode testStepsNode = rootNode.path("TestSteps");

            if (testStepsNode.isMissingNode() || !testStepsNode.isArray()) {
                return ResponseEntity.ok("No test steps found");
            }

            StringBuilder result = new StringBuilder();
            String resultFinal = null;
            for (JsonNode stepNode : testStepsNode) {
                String description = stepNode.path("Description").asText("").trim();
                String expectedResult = stepNode.path("ExpectedResult").asText("").trim();

                if (!description.isEmpty()) {
                    result.append(description).append("\n");
                }
                if (!expectedResult.isEmpty()) {
                    result.append(expectedResult).append("\n");
                }
                result.append("\n");
               resultFinal = result.toString().replaceAll("</?p>", "");
            }
System.out.println("API Parsed Result");
System.out.println(resultFinal);
            String featureFile = featureFileGenerator.generateFeatureFile(resultFinal.toString());
            return ResponseEntity.ok(featureFile);
            //return ResponseEntity.ok(Map.of("feature", featureFile));
        } catch (Exception e) {
            log.error("Error processing test case {}", request.getTestcaseId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing request: " + e.getMessage());
        }
    }
    public static class FeatureFileGenerationRequest {
        private String testcaseId;

        public String getTestcaseId() {
            return testcaseId;
        }
        public void setTestcaseId(String testcaseId) {
            this.testcaseId = testcaseId;
        }
    }
}