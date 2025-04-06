package com.testleaf.llm;

public class CodeGenerationResponse {
    private String featureFile;
    private String apiClass;
    private String pojos;
    private String stepDefinition;
    private String error;

    public CodeGenerationResponse() {}

    // Constructor for error cases.
    public CodeGenerationResponse(String error) {
        this.error = error;
    }

    public String getFeatureFile() {
        return featureFile;
    }
    public void setFeatureFile(String featureFile) {
        this.featureFile = featureFile;
    }
    public String getApiClass() {
        return apiClass;
    }
    public void setApiClass(String apiClass) {
        this.apiClass = apiClass;
    }
    public String getPojos() {
        return pojos;
    }
    public void setPojos(String pojos) {
        this.pojos = pojos;
    }
    public String getStepDefinition() {
        return stepDefinition;
    }
    public void setStepDefinition(String stepDefinition) {
        this.stepDefinition = stepDefinition;
    }
    public String getError() {
        return error;
    }
    public void setError(String error) {
        this.error = error;
    }
}
