# Logstash Output Plugin to Solace

A starter project, work-in-progress, for reading data off Solace PubSub+ event broker, and injecting into Logstash.  It is based on https://github.com/logstash-plugins/logstash-output-java_output_example and https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html.

## Building

```
./gradlew clean gem
```

Then use the `logstash-plugin` utility in your Logstash distribution to import the generated gem file.

To take advantage of Solace's dynamic hierarchical topic structure, you'll probably want to edit the `Solace.java` file to include whatever custom pieces of Logstash event metadata you want included in the topic.

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
