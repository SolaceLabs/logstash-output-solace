#### N.B. Work in progress... not production quality yet

# Logstash Output Plugin for Solace

A starter project, work-in-progress, for taking events out of Logstash and publishing as messages onto a Solace PubSub+ event broker.  It is based on https://github.com/logstash-plugins/logstash-output-java_output_example and https://www.elastic.co/guide/en/logstash/current/java-output-plugin.html.

To take advantage of Solace's dynamic hierarchical topic structure, you'll probably want to edit the `Solace.java` file to include whatever custom pieces of Logstash event metadata you want included in the topic.

## Building

You have to do a few steps before you can just build this.  Namely, you need to have a local copy of Logstash downloaded and built, so that this project can reference a compiled JAR file from it.  It follows the steps outlined in the Java output plugin example links above. 👆

1. Download a copy of Logstash source.  I cloned the 7.10 branch.  You can get other versions if you want.  https://github.com/elastic/logstash/tree/7.10
2. Set the environment variable `LS_HOME` to the directory where you saved Logstash.  E.g. ``export LS_HOME=`pwd` ``
3. Build Logstash.  E.g. `./gradlew assemble` from the Logstash directory.  (or `gradlew.bat` if Windows Command Prompt)
4. In the folder for _this_ project, create a new file `gradle.properties` with a single variable pointing to Logstash's built "core" directory.  E.g. mine looks like `LOGSTASH_CORE_PATH=../logstash-7.10/logstash-core`  as I have Logstash and this plugin in sibling directories.  Or can use an absolute path if you want.
5. You are now ready to compile this project. From the output plugin home directory:

```
./gradlew clean gem
```

This will generate a file that looks something like `logstash-output-solace-x.y.z.gem`.  Use the `logstash-plugin` utility in your Logstash distribution to import the generated gem file. Something like:
```
bin/logstash-plugin install --no-verify --local /home/alee/logstash-output-solace-0.0.3.gem
```

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


### Solace message

As of right now, the payload of the message will be a JSON object, which parses through the contents of the Logstash event and puts them in the object.  Obviously you'd probably want to change this behaviour, based on the type
of message you have.  Same with the published topic... add some custom code to publish on useful dynamic hierarchical Solace topics (e.g. `#LOG/<host>/<vpn>/<severity>/pid` or something).

## Logstash API API

Useful reference for coding.
https://github.com/elastic/logstash/tree/master/logstash-core/src/main/java/co/elastic/logstash/api
