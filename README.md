# Logstash Output Plugin to Solace

A starter project, work-in-progress, for taking events out of Logstash and publishing as messages onto a Solace PubSub+ event broker.  It is based on https://github.com/logstash-plugins/logstash-output-java_output_example and https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html.

To take advantage of Solace's dynamic hierarchical topic structure, you'll probably want to edit the `Solace.java` file to include whatever custom pieces of Logstash event metadata you want included in the topic.

## Building

```
./gradlew clean gem
```

Then use the `logstash-plugin` utility in your Logstash distribution to import the generated gem file.

## Example config:

```
output {
  solace {
    host => "192.168.42.35"
    vpn => "stats"
    username => "statspump"
    password => "password"
  }
  stdout { codec => rubydebug { "metadata" => "true" } }
}
```

### Parameters

- `host`: (optional, will default to "localhost") comma-separated list of Solace brokers to connect to
- `vpn`: (optional, will default to "default") name of the Message VPN to connect to
- `username`: (optional, will default to "default") client-username to connect with
- `password`: (optional, will default to "default") password for the client-username
- `topic`: (optional, will default to "#logstash") Solace topic to publish messages to.
    - N.B. if Logstash event @metadata contains "sol-topic" then that topic will be appended to this value.


### Logstash Event Metadata

The following @metadata fields will be populated by the plugin:

- `solace-topic`: the Destination the message was published to
- `solace-delivery-mode`: the message's DeliveryMode, either "DIRECT" or "PERSISTENT"
- `solace-application-message-id`: (optional) if the message's Application Message ID (aka JMS Message ID) is populated
- `solace-application-message-type`: (optional) if the message's Application Message Type (aka JMS Message Type) is configured
- `solace-reply-to`: (optional) if the message's reply-to is configured
- `solace-correlation-id`: (optional) if the message's Correlation ID is configured
- `solace-sequence-number`: (optional) if the message's Long sequence number is set
- `@timestamp`: the Logstash event's timestamp will be updated with `msg.getSenderTimestamp()` if populated

