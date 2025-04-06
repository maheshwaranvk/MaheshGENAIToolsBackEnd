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
public class FeatureFileGenerator {

    private final String systemPrompt = "You are an expert in converting Spira test cases into Cucumber feature files. " +
            "You are an intelligent assistant responsible for generating clean, production-ready Gherkin Feature Files from a given Spira Test Case ID.\n" +
            "The user will provide a Spira Test Case ID as input.\n" +
            "You must connect to the Spira API or test case store, fetch the relevant details (test case name, steps, parameters, expected results).\n" +
            "Use best practices of Behavior-Driven Development (BDD) to convert the test case into a structured Gherkin format with:\n" +
            "- Optional Background if preconditions are shared\n" +
            "- Clearly defined Scenario or Scenario Outline blocks\n" +
            "- Tables for data wherever appropriate (Examples, Given preconditions, etc.)\n" +
            "Do not include any internal or system-specific details from Spira (IDs, internal notes, etc.) unless explicitly instructed.\n" +
            "Maintain natural language clarity and readability — your output should be suitable for use by QA, developers, and business stakeholders alike.\n" +
            "Always output plain text, not JSON. Avoid escaping characters like <, > or newlines.\n" +
            "Your goal is to bridge the gap between manual test cases and automated BDD testing by translating structured Spira test cases into expressive, readable Gherkin Feature Files.\n" +
            "Follow these rules strictly:\n" +
            "1. Always include a Background section for user creation with data table\n" +
            "2. Use PascalCase for scenario tags (Example: @VerifySendTextMessage)\n" +
            "3. Combine similar verification steps for multiple users\n" +
            "4. Use Scenario Outline when steps are duplicated\n" +
            "5. Include student data tables when relevant\n" +
            "6. Maintain professional, concise step definitions";


    @Value("${llm.api.url}")
    private String llmApiUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.model}")
    private String modelName;

    public String generateFeatureFile(String testSteps) {
        if (testSteps == null || testSteps.isEmpty()) {
            return "Error: No test steps provided";
        }

        String userPrompt = buildUserPrompt(testSteps);

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", modelName);
            payload.put("messages", messages);
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 4000);

            String requestBody = new ObjectMapper().writeValueAsString(payload);
            String llmResponse = callLLMApiForFF(requestBody);

            return extractFeatureFile(llmResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating feature file: " + e.getMessage();
        }
    }

    private String buildUserPrompt(String testSteps) {
        return "[Context]\n" +
                "- Given Manual Testcase should be converted into Feature File\n" +
                "- Test Steps:\n" +
                "Create two users sender, receiver\n" +
                "sender have permissions to view confidential student and receiver doesnt have\n" +
                "caller navigates to Receiver's chat\n" +
                "caller clicks attachment button\n" +
                "caller selects 'Students' from attachment button. Student list screen should open\n" +
                "caller selects student 'Ragu' from the list. Caller verifies the student'Ragu' is added a link in chatbox\n" +
                "caller clicks send button\n" +
                "receiver verifies the hyperlink student message named 'Ragu'\n" +
                "receiver clicks on the hyperlink student message named 'Ragu'\n" +
                "receiver verifies the student 'Ragu' details\n\n" +
                "Create two users sender, receiver\n" +
                "sender have permissions to view confidential student and receiver doesnt have\n" +
                "caller navigates to Receiver's chat\n" +
                "caller clicks attachment button\n" +
                "caller selects 'Students' from attachment button. Student list screen should open\n" +
                "caller selects student 'Raju' from the list. Caller verifies the student'Raju' is added a link in chatbox\n" +
                "caller clicks send button\n" +
                "receiver verifies the hyperlink student message named 'Raju'\n" +
                "receiver clicks on the hyperlink student message named 'Raju'\n" +
                "receiver verifies the student 'Raju' details and all details were present as '*****'\n\n" +
                "- The Feature File generate will be used by Automation Team\n" +
                "- Given Manual Testcase is for a Communication application\n\n" +
                "[Instruction]\n" +
                "- You are a Manual Tester who have high knowledge and expertise in writing Feature File from Cucumber\n" +
                "- Feature File generated should have a Background step where it is creating the users\n" +
                "- Users creation step should have a Data Table for userName, firstName, lastName, location, provider, canViewConfidential\n" +
                "- Provider can be either 'Airtel', 'JIO', 'Vodaphone'\n" +
                "- Each Scenario should have a tag name which should be written in pascal notation\n" +
                "- Since similar steps exist for two students, convert it into Scenario Outline with Examples\n" +
                "- Add a Student creation step in background as a Data Table with studentName, studentRollNumber, studentDepartment, studentDOB, isConfidentialStudent\n" +
                "- Verification steps should show actual data for sender and masked data (***) for receiver\n\n" +
                "- Output should be without \n but it should be in proper gherkin format"+
                "[Required Output Format]\n" +
                "```gherkin\n" +
                "Background:\n" +
                "  Given Following users are created\n" +
                "    | userName | firstName | lastName | location   | provider | canViewConfidential |\n" +
                "    | sender   | Peter     | Parker   | Trivandrum | Airtel   | Yes                 |\n" +
                "    | receiver | John      | Wick     | Chennai    | JIO      | No                  |\n\n" +
                "  And Following student details are available\n" +
                "    | studentName | studentRollNumber | studentDepartment | studentDOB  | isConfidentialStudent |\n" +
                "    | Ragu        | 101               | Computer Science  | 1995-05-15  | Yes                   |\n" +
                "    | Raju        | 102               | Electrical Engg   | 1996-07-20  | Yes                   |\n\n" +
                "@VerifyConfidentialStudentSharing\n" +
                "Scenario Outline: Verify confidential student information sharing\n" +
                "  Given User logs in: sender, receiver\n" +
                "  And sender navigates to receiver's chat\n" +
                "  When sender attaches student '<student>' and verifies link appears in chatbox - sender\n" +
                "  And sender sends the student link\n" +
                "  Then receiver verifies hyperlink message '<student>' appears - receiver\n" +
                "  When receiver clicks on student '<student>' link and verifies details - receiver\n" +
                "    | studentName | studentRollNumber | studentDepartment | studentDOB  | isConfidentialStudent |\n" +
                "    | <student>   | ***               | ******            | ******      | No                    |\n\n" +
                "  Examples:\n" +
                "    | student |\n" +
                "    | Ragu    |\n" +
                "    | Raju    |\n" +
                "```\n\n" +
                "[Rules]\n" +
                "1. Always show actual data for sender and masked data (***) for receiver\n" +
                "2. Combine steps for multiple users where possible\n" +
                "3. Use professional, concise language\n" +
                "4. Return ONLY the feature file content between ```gherkin``` markers";
    }

    private String extractFeatureFile(String llmResponse) {
        Pattern pattern = Pattern.compile("```gherkin\\s*(.*?)\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(llmResponse);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Error: No feature file found in LLM response";
    }

    public String callLLMApiForFF(String requestBody) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(llmApiUrl);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error calling LLM API: " + e.getMessage();
        }
    }
}