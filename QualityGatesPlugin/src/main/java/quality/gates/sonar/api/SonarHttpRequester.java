package quality.gates.sonar.api;

import quality.gates.jenkins.plugin.GlobalConfigDataForSonarInstance;
import quality.gates.jenkins.plugin.JobConfigData;
import quality.gates.jenkins.plugin.QGException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class SonarHttpRequester {

	private static final String SONAR_API_VERSION = "/api/server/version";
    private static final String SONAR_API_GATE = "/api/events?resource=%s&format=json&categories=Alert";
    private static final String SONAR_API_GATE_NEW = "/api/project_analyses/search?project=%s&category=QUALITY_GATE";

    private transient HttpClientContext context;

    public SonarHttpRequester() {
    }

    public Float getSonarQubeVersion(GlobalConfigDataForSonarInstance globalConfigDataForSonarInstance) throws QGException {
    	String sonarVersionAPI = globalConfigDataForSonarInstance.getSonarUrl() + SONAR_API_VERSION;
    	
    	context = HttpClientContext.create();
        CloseableHttpClient client = HttpClientBuilder.create().build();
    	
    	HttpGet request = new HttpGet(sonarVersionAPI);
    	String result = executeGetRequest(client, request);
    	String[] resultTab = result.split("\\.");
    	if(resultTab.length >= 2) {
    		try {
    			int majorVersion = Integer.parseInt(resultTab[0]);
    			int minorVersion = Integer.parseInt(resultTab[1]);
    			
    			return Float.parseFloat(majorVersion+"."+minorVersion);
    			
    		} catch (NumberFormatException e) {
    			throw new QGException("Can't parse SonarQube version : "+result);
    		}
    	} else {
    		throw new QGException("Can't find major version of SonarQube : "+result);
    	}
    }
    
    private String getApiInfo(JobConfigData projectKey, GlobalConfigDataForSonarInstance globalConfigDataForSonarInstance, String sonarApiGate) throws QGException {
    	context = HttpClientContext.create();
        CloseableHttpClient client = HttpClientBuilder.create().build();
             
        HttpPost loginHttpPost = new HttpPost(globalConfigDataForSonarInstance.getSonarUrl() + "/sessions/login");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("login", globalConfigDataForSonarInstance.getUsername()));
        nvps.add(new BasicNameValuePair("password", globalConfigDataForSonarInstance.getPass()));
        nvps.add(new BasicNameValuePair("remember_me", "1"));
        loginHttpPost.setEntity(createEntity(nvps));
        loginHttpPost.addHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
        executePostRequest(client, loginHttpPost);
        HttpGet request = new HttpGet(String.format(sonarApiGate, projectKey.getProjectKey()));
        return executeGetRequest(client, request);
    }
    
    public String getAPIInfo(JobConfigData projectKey, GlobalConfigDataForSonarInstance globalConfigDataForSonarInstance) throws QGException {
        String sonarApiGate = globalConfigDataForSonarInstance.getSonarUrl() + String.format(SONAR_API_GATE, projectKey.getProjectKey());
        
        return getApiInfo(projectKey, globalConfigDataForSonarInstance, sonarApiGate);
    }
    
    public String getAPIInfoNew(JobConfigData projectKey, GlobalConfigDataForSonarInstance globalConfigDataForSonarInstance) throws QGException {
    	String sonarApiGate = globalConfigDataForSonarInstance.getSonarUrl() + String.format(SONAR_API_GATE_NEW, projectKey.getProjectKey());
    	
    	return getApiInfo(projectKey, globalConfigDataForSonarInstance, sonarApiGate);
    }

    private String executeGetRequest(CloseableHttpClient client, HttpGet request) throws QGException {
        CloseableHttpResponse response = null;
        try {
            response =  client.execute(request, context);
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String returnResponse = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            if (statusCode != 200) {
                throw new QGException("Expected status 200, got: " + statusCode + ". Response: " + returnResponse);
            }
            return returnResponse;
        } catch (IOException e) {
            throw new QGException("GET execution error", e);
        } finally {
            try {
                if (response != null)
                    response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void executePostRequest(CloseableHttpClient client, HttpPost loginHttpPost) throws QGException {
        try {
            client.execute(loginHttpPost);
        }
        catch (IOException e) {
            throw new QGException("POST execution error", e);
        }
    }

    private UrlEncodedFormEntity createEntity(List<NameValuePair> nvps) throws QGException {
        try {
            return new UrlEncodedFormEntity(nvps);
        }
        catch (UnsupportedEncodingException e){
            throw new QGException("Encoding error", e);
        }
    }
}
