package com.testleaf.llm;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class LLMCompleteCodeGenerator {

    private final String systemPrompt = "You are a helpful assistant that generates complete Java automation test code for Salesforce APIs. " +
        "Follow RestAssured and TestNG standards. Return only Java code and Gherkin feature files enclosed in triple backticks. " +
        "Maintain these conventions: Use automation.tests package, use TestNG, validate status codes, and generate before-methods for base URI setup. " +
        "Do not include explanatory text or comments.";

    private final String additionalInstructions = "- Use TestNG and RestAssured (Java 8 compatible).\n" +
        "- Follow standard assertions (status codes only).\n" +
        "- Use BeforeMethod of TestNG to set the base URI.\n";

    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.model}")
    private String modelName;

    public CodeGenerationResponse generateCompleteCode(String apiDetails, List<String> testTypes) {
        if (apiDetails == null || apiDetails.isEmpty()) {
            return new CodeGenerationResponse("No valid API details to generate code.");
        }

        String testTypeLine = buildTestTypeLine(testTypes);
        String systemContent = systemPrompt + "\n" + testTypeLine + additionalInstructions;

        String userPrompt = buildUserPrompt(apiDetails);

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemContent));
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", modelName);
            payload.put("messages", messages);
            payload.put("temperature", 0.1);
            payload.put("top_p", 0.2);
            payload.put("max_tokens", 15000);

            String requestBody = new ObjectMapper().writeValueAsString(payload);
            String llmResponse = callLLMApi(requestBody);

            return extractCodeBlocks(llmResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return new CodeGenerationResponse("Error building JSON payload: " + e.getMessage());
        }
    }

    public CodeGenerationResponse generateCompleteCode(String apiDetails) {
        return generateCompleteCode(apiDetails, new ArrayList<>());
    }

    private String buildTestTypeLine(List<String> testTypes) {
        if (testTypes == null || testTypes.isEmpty()) return "- Include only Positive scenarios.\n";

        List<String> validTypes = new ArrayList<>();
        testTypes.forEach(type -> {
            switch (type.toLowerCase()) {
                case "positive": validTypes.add("Positive"); break;
                case "negative": validTypes.add("Negative"); break;
                case "edge": validTypes.add("Edge"); break;
            }
        });

        if (validTypes.isEmpty()) return "- Include only Positive scenarios.\n";
        return "- Include " + String.join(" and ", validTypes) + " scenarios.\n";
    }

    private String buildUserPrompt(String apiDetails) {
        return "Instruction:\n" +
            "Using the given parsed Salesforce Swagger API details, generate complete RestAssured test code for the CREATE & DELETE operation only.\n\n" +
            "Context:\n" +
            "The provided details are from a Salesforce OpenAPI (Swagger) specification, including title, version, description, servers, paths, and components/schemas. " +
            "The generated code must be generic and adaptable to any Salesforce Swagger document. Do not hardcode resource names; use generic names like 'Resource'.\n\n" +
            "Required Output:\n" +
            "1) A Gherkin Feature File containing a scenario for creating and deleting a resource with valid data.\n" +
            "   Example for Create Request:\n" +
            "   ```gherkin\n" +
            "   Feature: Resource API\n" +
            "     Scenario: Create a resource with valid data\n" +
            "       Given I have valid resource details\n" +
            "       When I create a new resource\n" +
            "       Then the response status should be 201\n" +
            "   ```\n" +
            "   Example for Delete Request:\n" +
            "   ```gherkin\n" +
            "   Feature: Resource API\n" +
            "     Scenario: Delete the created resource\n" +
            "       Given I have an existing resource as '701J1000000HKytIAG'\n" +
            "       When I delete the resource\n" +
            "       Then the response status should be 204\n" +
            "   ```\n" +
            "\n" +
            "2) A generic API Java Class (e.g., ResourceAPI.java) with a method "+ 
            "   Create a Resource : \n"+
            "   createResource(CreateResourceRequest request) that returns a CreateResponse.\n" +
            "   NOTE: Do NOT create CreateResponse class as it already exist in framework, so use it "+
            "   Delete an Existing Resource : \n"+
            "   deleteResource(String ResourceId) that returns a Response.\n" +
            "   NOTE: Do NOT Change the end point path .. just change the Resource only "+
            "   MANDATORY: The Resource name should change (like Contact, Account, Opportunity) based on the API document "+
            "   Example:\n" +
            "   ```java\n" +
            "   package api;\n" +
            "   \n" +
            "   import io.restassured.response.Response;\n" +
            "   import pojos.CreateResourceRequest;\n" +
            "   import pojos.CreateResponse;\n" +
            "   import pojos.BaseResponse;\n" +
            "   \n" +
            "   public class ResourceAPI extends BaseAPI {\n" +
            "       private static final String RESOURCE_ENDPOINT = \"/Resource\";\n" +
            "       \n" +
            "       // Create Resource\n" +
            "       public CreateResponse createResource(CreateResourceRequest request) {\n" +
            "           String endpoint = baseUrl + RESOURCE_ENDPOINT;\n" +
            "           Response response = post(endpoint, request, getAuthHeaders());\n" +
            "           try {\n" +
            "               CreateResponse createResponse = mapper.readValue(response.asString(), CreateResponse.class);\n" +
            "               createResponse.setStatusCode(response.getStatusCode());\n" +
            "               return createResponse;\n" +
            "           } catch (Exception e) {\n" +
            "               throw new RuntimeException(\"Failed to parse Create Resource response\", e);\n" +
            "           }\n" +
            "       }\n" +
            "		public BaseResponse deleteResource(String ResourceId) { \n"+
	        "    		String endpoint = baseUrl + RESOURCE_ENDPOINT + \"/\" + parameter; \n"+
	        "	    	Response response = delete(endpoint, getAuthHeaders());\n" +
	        "        	try {\n" +
	        "            	BaseResponse deleteResponse = new BaseResponse();\n" +
	        "            	deleteResponse.setStatusCode(response.getStatusCode());\n" +
	        "            	return deleteResponse;\n" +
	        "        	} catch (Exception e) {\n" +
	        "            	throw new RuntimeException(\"Failed to parse Delete Resource response\", e);\n" +
	        "        	}\n" +
	        "    	}\n" +
            "   }\n" +
            "   ```\n" +
            "\n" +
            "3) POJO Classes for the create operation: CreateResourceRequest ONLY\n" +
            "   NOTE: I do not need Response POJO for create operation "+
            "   Example:\n" +
            "   ```java\n" +
            "   package pojos;\n" +
            "   \n" +
            "	import com.fasterxml.jackson.annotation.JsonIgnoreProperties;\n" +
            "	import com.fasterxml.jackson.annotation.JsonInclude;\n" +
            "	\n"+
            "	@JsonIgnoreProperties(ignoreUnknown = true)\n" +
            "	@JsonInclude(JsonInclude.Include.NON_NULL)\n" +
            "   \n" +
            "   public class CreateResourceRequest {\n" +
            "       private String name;\n" +
            "       private String email;\n" +
            "       private String phone;\n" +
            "       // Getters and setters...\n" +
            "   }\n"+
            "   ```\n" +
            "\n" +
            "4) A Step Definition Java Class mapping the feature file steps to the API class method for the create and delete operation.\n" +
            "   No Step definition required for 'response status should be' assertion as it is available in base class \n" +
            "   IMPORTANT: Use faker for names, phone number, email, amount etc. based on Salesforce fields \n" +
            "   Example:\n" +
            "   ```java\n" +
            "   package steps;\n" +
            "   \n" +
            "   import api.ResourceAPI;\n" +
            "   import pojos.BaseResponse;\n" +
            "	import context.ResponseContext;\n"+
            "   import pojos.CreateResourceRequest;\n" +
            "   import pojos.CreateResponse;\n" +
            "   import io.cucumber.java.en.Given;\n" +
            "   import io.cucumber.java.en.When;\n" +
            "   import org.testng.Assert;\n" +
            "   \n" +
            "   public class ResourceSteps {\n" +
            "       private ResourceAPI resourceAPI;\n" +
            "       private CreateResourceRequest resourceRequest;\n" +
            "		private final ResponseContext context;\n"+
            "       private CreateResponse createResponse;\n" +
            "       \n" +
            "		public ResourceSteps(ResponseContext context) { \n"+
            "           resourceAPI = new ResourceAPI();\n" +
            "		    this.context = context; \n"+
            "		} \n"+
            "       @Given(\"I have valid resource details\")\n" +
            "       public void iHaveValidResourceDetails() {\n" +
            "           resourceRequest = new CreateResourceRequest();\n" +
            "           resourceRequest.setName(\"Test Resource\");\n" +
            "       }\n" +
            "       \n" +
            "       @When(\"I create a new resource\")\n" +
            "       public void iCreateANewResource() {\n" +
            "           createResponse = resourceAPI.createResource(resourceRequest);\n" +
            "           Assert.assertNotNull(createResponse.getId(), \"Resource id should not be null\");\n" +
            "			context.setBaseResponse(createResponse);\n"+
            "       }\n" +
			"       @Given(\"I have an existing resource as {string}\")\n" +
            "		public void iHaveExistingResource(String parameter) {\n" +
            "			context.setId(parameter);\n " +
            "		}\n" +
            "		@When(\"I delete the resource\")\n" +
            "		public void iDeleteTheResource() {\n" +
            "		    BaseResponse deleteResponse = resourceAPI.deleteResource(context.getId());\n" +
            "		    context.setBaseResponse(deleteResponse);\n" +
            "		}\n" +
            "   }\n" +
            "   ```\n" +
            "\n" +
            "Tone:\n" +
            "Professional, concise, and structured.\n\n" +
            "Output Format:\n" +
            "Return only the code, with each section enclosed in triple backticks using appropriate language tags:\n" +
            " - First block: ```gherkin``` (Feature file)\n" +
            " - Second block: ```java``` (API class)\n" +
            " - Third block: ```java``` (POJO classes)\n" +
            " - Fourth block: ```java``` (Step Definitions)\n\n" +
            "Persona:\n" +
            "You are an expert assistant that generates complete Java code for API tests using RestAssured and TestNG, following our framework conventions.\n\n" +
            apiDetails;
    }


    private CodeGenerationResponse extractCodeBlocks(String llmResponse) {
        CodeGenerationResponse response = new CodeGenerationResponse();
        Pattern pattern = Pattern.compile("```(\\w+)\\s*(.*?)\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(llmResponse);

        int blockCount = 0;
        while (matcher.find()) {
            String code = matcher.group(2).trim();
            blockCount++;

            switch (blockCount) {
                case 1 -> response.setFeatureFile(code);
                case 2 -> response.setApiClass(code);
                case 3 -> response.setPojos(code);
                case 4 -> response.setStepDefinition(code);
            }
        }

        // Fallbacks for missing blocks
        if (response.getFeatureFile() == null) response.setFeatureFile("/* Feature file not generated */");
        if (response.getApiClass() == null) response.setApiClass("/* API class not generated */");
        if (response.getPojos() == null) response.setPojos("/* POJO classes not generated */");
        if (response.getStepDefinition() == null) response.setStepDefinition("/* Step definition class not generated */");

        return response;
    }

    private String callLLMApi(String requestBody) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(llmApiUrl);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setEntity(new StringEntity(requestBody));

            System.out.println("Sending request to LLM...");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling LLM API: " + e.getMessage();
        }
    }
}
