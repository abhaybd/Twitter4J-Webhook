package com.abhaybd.twitter4jwebhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public class SubscriptionInfo {
    @JsonProperty
    private String environment;
    @JsonProperty("application_id")
    private String applicationID;
    @JsonProperty("subscriptions")
    private User[] users;

    public long[] getUsers() {
        return Arrays.stream(users).map(u -> u.id).mapToLong(Long::parseLong).toArray();
    }

    private static class User {
        @JsonProperty("user_id")
        public String id;
    }
}
