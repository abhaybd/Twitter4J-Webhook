# Twitter4J-Webhook

Manage the Twitter account activity API. This adds some classes that can be used with [Twitter4J](https://github.com/Twitter4J/Twitter4J) to manage webhooks, as well as subscriptions to those webhooks.

## Installation

Install with maven

```maven
<dependency>
  <groupId>com.abhaybd</groupId>
  <artifactId>twitter4j-webhook</artifactId>
  <version>0.0.1</version>
</dependency>
```

## Usage

Create a webhook manager

```Java
Twitter twitter = TwitterFactory.getSingleton();
WebhookManager manager = new WebhookManager(twitter.getConfiguration(), "ENV_NAME");
```

Register a webhook at the given URI

```java
manager.addWebhook(URI.create("https://example.webhook.com/webhook"));
```

Unregister the currently registered webhook

```Java
manager.removeWebhook();
```

Get the information for the currently registered webhook

```Java
WebhookInfo info = manager.getWebhookInfo();
System.out.println(info.getID());
System.out.println(info.isValid());
```

Trigger a [CRC check](https://developer.twitter.com/en/docs/twitter-api/v1/accounts-and-users/subscribe-account-activity/guides/securing-webhooks) for the currently registered webhook

```java
manager.triggerCRC();
```

Register the currently authenticated user to this webhook

```java
manager.registerCurrentUser();
```

Unregister all users from this webhook

```Java
manager.unregisterAllUsers();
```

Unregister a specific user from this webhook

```Java
long userId = 1024L;
manager.unregisterUser(userId);
```

