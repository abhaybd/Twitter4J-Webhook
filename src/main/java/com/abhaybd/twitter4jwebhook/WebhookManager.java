package com.abhaybd.twitter4jwebhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import twitter4j.HttpParameter;
import twitter4j.HttpRequest;
import twitter4j.RequestMethod;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.conf.Configuration;

public class WebhookManager {
    private static final String GENERAL_HOOK_ENDPOINT = "https://api.twitter.com/1.1/account_activity/all/%s/webhooks.json";
    private static final String ADD_HOOK_ENDPOINT = GENERAL_HOOK_ENDPOINT + "?url=%s";
    private static final String SPECIFIC_HOOK_ENDPOINT = "https://api.twitter.com/1.1/account_activity/all/%s/webhooks/%d.json";
    private static final String BEARER_TOKEN_ENDPOINT = "https://api.twitter.com/oauth2/token";
    private static final String SUBSCRIPTIONS_ENDPOINT = "https://api.twitter.com/1.1/account_activity/all/%s/subscriptions.json";
    private static final String UNSUBSCRIBE_ENDPOINT = "https://api.twitter.com/1.1/account_activity/all/%s/subscriptions/%d.json";
    private static final String LIST_SUBSCRIPTIONS_ENDPOINT = "https://api.twitter.com/1.1/account_activity/all/%s/subscriptions/list.json";

    private final Configuration configuration;
    private final String env;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper mapper;
    private String bearerToken;

    public WebhookManager(Configuration configuration, String env) {
        this.configuration = Objects.requireNonNull(configuration);
        this.env = Objects.requireNonNull(env);
        httpClient = HttpClients.createDefault();
        mapper = new ObjectMapper();
    }

    public WebhookInfo getWebhookInfo() {
        try {
            // Send a GET request to the API endpoint
            String endpoint = String.format(GENERAL_HOOK_ENDPOINT, env);
            HttpGet request = new HttpGet(endpoint);
            request.addHeader("Authorization", "Bearer " + getBearerToken());
            CloseableHttpResponse response = httpClient.execute(request);
            // Parse the response
            WebhookInfo[] webhooks = mapper.readValue(response.getEntity().getContent(), WebhookInfo[].class);
            // Return the id of the first valid webhook (generally the only one)
            return Arrays.stream(webhooks).filter(WebhookInfo::isValid).findAny().orElse(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode triggerCRC() {
        try {
            WebhookInfo info = getWebhookInfo();
            if (info == null) return StatusCode.createError("Error: No webhook for which to trigger CRC!");
            long id = info.getID();
            // Send a PUT request to the API endpoint
            String endpoint = String.format(SPECIFIC_HOOK_ENDPOINT, env, id);
            HttpPut request = new HttpPut(endpoint);
            // Add the authorization header with the correct keys/tokens
            request.addHeader("Authorization", authorizationHeader(request));
            CloseableHttpResponse response = httpClient.execute(request);
            return getStatusCode(response, 204);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode addWebhook(URI uri) {
        try {
            // Send a POST request to the API endpoint
            String url = uri.toASCIIString();
            String encodedWebhookUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.name());
            String endpoint = String.format(ADD_HOOK_ENDPOINT, env, encodedWebhookUrl);
            HttpPost request = new HttpPost(endpoint);
            // Add the authorization header with the correct keys/tokens
            request.addHeader("Authorization", authorizationHeader(request));
            CloseableHttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            // if response is HTTP OK then no error
            if (statusCode == 200) {
                return StatusCode.createSuccess(statusCode, response.getStatusLine().getReasonPhrase());
            } else {
                // If an error occurred, read the error message and return it
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                response.getEntity().writeTo(os);
                return StatusCode.createError(statusCode, os.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode registerCurrentUser() {
        String endpoint = String.format(SUBSCRIPTIONS_ENDPOINT, env);
        HttpPost request = new HttpPost(endpoint);
        request.addHeader("Authorization", authorizationHeader(request));
        try {
            CloseableHttpResponse response = httpClient.execute(request);
            return getStatusCode(response, 204);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode removeWebhook() {
        try {
            // Check if a webhook exists and get its id
            WebhookInfo info = getWebhookInfo();
            if (info == null) return StatusCode.OK;

            long id = info.getID();
            // Send a DELETE request to the API endpoint
            String endpoint = String.format(SPECIFIC_HOOK_ENDPOINT, env, id);
            HttpDelete request = new HttpDelete(endpoint);
            // Add the authorization header with the correct keys/tokens
            request.addHeader("Authorization", authorizationHeader(request));
            CloseableHttpResponse response = httpClient.execute(request);
            // If the response was HTTP OK then it was successful.
            return getStatusCode(response, 204);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode unregisterAllUsers() {
        try {
            String endpoint = String.format(LIST_SUBSCRIPTIONS_ENDPOINT, env);
            HttpGet request = new HttpGet(endpoint);
            request.addHeader("Authorization", "Bearer " + getBearerToken());
            CloseableHttpResponse response = httpClient.execute(request);
            // Parse the response
            ObjectMapper mapper = new ObjectMapper();
            SubscriptionInfo info = mapper.readValue(response.getEntity().getContent(), SubscriptionInfo.class);

            boolean success = true;
            for (long id : info.getUsers()) {
                success &= !unregisterUser(id).isError;
            }
            return success ? StatusCode.OK : StatusCode.createError(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public StatusCode unregisterUser(long id) {
        try {
            String endpoint = String.format(UNSUBSCRIBE_ENDPOINT, env, id);
            HttpDelete request = new HttpDelete(endpoint);
            request.addHeader("Authorization", "Bearer " + getBearerToken());
            CloseableHttpResponse response = httpClient.execute(request);
            return getStatusCode(response, 204);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private StatusCode getStatusCode(CloseableHttpResponse response, int expectedStatus) {
        int status = response.getStatusLine().getStatusCode();
        String phrase = response.getStatusLine().getReasonPhrase();
        if (status == expectedStatus) {
            return StatusCode.createSuccess(status, phrase);
        } else {
            return StatusCode.createError(status, phrase);
        }
    }

    private String authorizationHeader(HttpRequestBase req) {
        String url = req.getURI().toASCIIString(); // even though it might be a POST request, the webhook will be in the url
        // Twitter4J did all the heavy lifting, so lets not reinvent the wheel
        OAuthAuthorization auth = new OAuthAuthorization(configuration);
        RequestMethod reqMethod = RequestMethod.valueOf(req.getMethod().toUpperCase());
        HttpRequest httpReq = new HttpRequest(reqMethod, url, new HttpParameter[0], null, new HashMap<>());
        return auth.getAuthorizationHeader(httpReq);
    }

    private String getBearerToken() {
        // If we don't have the bearer token, send a request to Twitter to get one now
        if (bearerToken == null) {
            try {
                HttpPost request = new HttpPost(BEARER_TOKEN_ENDPOINT);

                // authorize the request with the consumer key and secret
                String key = configuration.getOAuthConsumerKey();
                String secret = configuration.getOAuthConsumerSecret();
                UsernamePasswordCredentials cred = new UsernamePasswordCredentials(key, secret);
                request.addHeader(new BasicScheme().authenticate(cred, request, null));

                // Add the required headers and content
                request.setEntity(new StringEntity("grant_type=client_credentials"));
                request.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

                // Execute the request and deserialize the response JSON
                CloseableHttpResponse response = httpClient.execute(request);
                BearerToken token = mapper.readValue(response.getEntity().getContent(), BearerToken.class);

                bearerToken = token.getAccessToken();
            } catch (IOException | AuthenticationException e) {
                throw new RuntimeException(e);
            }
        }

        return bearerToken;
    }

    private static class BearerToken {
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("access_token")
        private String accessToken;

        public String getAccessToken() {
            return accessToken;
        }
    }
}
