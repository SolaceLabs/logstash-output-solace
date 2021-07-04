# Logstash Output Plugin to Solace

A starter project, work-in-progress, for reading data off Solace PubSub+ event broker, and injecting into Logstash.  It is based on https://github.com/logstash-plugins/logstash-output-java_output_example and https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html.


To take advantage of Solace's dynamic hierarchical topic structure, you'll probably want to edit the `Solace.java` file to include whatever custom pieces of Logstash event metadata you want included in the topic.
