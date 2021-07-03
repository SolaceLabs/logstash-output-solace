package com.solace.aaron.logstash.output;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.Output;
import co.elastic.logstash.api.Password;
import co.elastic.logstash.api.PluginConfigSpec;
import com.solacesystems.jcsmp.JCSMPChannelProperties;
import com.solacesystems.jcsmp.JCSMPErrorResponseException;
import com.solacesystems.jcsmp.JCSMPErrorResponseSubcodeEx;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishCorrelatingEventHandler;
import com.solacesystems.jcsmp.JCSMPTransportException;
import com.solacesystems.jcsmp.SessionEventArgs;
import com.solacesystems.jcsmp.SessionEventHandler;
import com.solacesystems.jcsmp.TextMessage;
import com.solacesystems.jcsmp.XMLMessageProducer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import org.logstash.Timestamp;

// class name must match plugin name
@LogstashPlugin(name = "solace")
public class Solace implements Output {

    //public static final PluginConfigSpec<String> PREFIX_CONFIG = PluginConfigSpec.stringSetting("prefix", " > ");

    public static final PluginConfigSpec<String> HOST_CONFIG = PluginConfigSpec.stringSetting("host", "localhost");
    public static final PluginConfigSpec<String> VPN_CONFIG = PluginConfigSpec.stringSetting("vpn", "default");
    public static final PluginConfigSpec<String> USERNAME_CONFIG = PluginConfigSpec.stringSetting("username", "default");
    public static final PluginConfigSpec<Password> PASSWORD_CONFIG = PluginConfigSpec.passwordSetting("password", "default", false, false);

    public static final PluginConfigSpec<String> TOPIC_CONFIG = PluginConfigSpec.stringSetting("topic", "#logstash");
    public static final PluginConfigSpec<String> TOPIC_PREFIX_CONFIG = PluginConfigSpec.stringSetting("topic-prefix", "#logstash");

    public static final List<PluginConfigSpec<?>> CONFIG_OPTIONS = new ArrayList<>();
    static {
        CONFIG_OPTIONS.add(HOST_CONFIG);
        CONFIG_OPTIONS.add(VPN_CONFIG);
        CONFIG_OPTIONS.add(USERNAME_CONFIG);
        CONFIG_OPTIONS.add(PASSWORD_CONFIG);
        CONFIG_OPTIONS.add(TOPIC_CONFIG);
        CONFIG_OPTIONS.add(TOPIC_PREFIX_CONFIG);
    }

    
    private final String id;
    private final Configuration config;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped = false;
    
    //private final JsonBuilderFactory factory = Json.createBuilderFactory(null);
    private JCSMPSession session;
    private final XMLMessageProducer producer;
    private StringBuilder sb = new StringBuilder();
    
    private final SessionEventHandler sessionEventHandler = new SessionEventHandler() {
        @Override
        public void handleEvent(SessionEventArgs event) {
            System.out.printf("### Received a Session event: %s%n",event);
        }
    };
    
    private final JCSMPStreamingPublishCorrelatingEventHandler pubHandler = new JCSMPStreamingPublishCorrelatingEventHandler() {

        @Override public void responseReceivedEx(Object key) {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
        }

        // can be called for ACL violations, connection loss, and Persistent NACKs
        @Override
        public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
            System.out.printf("### Producer handleErrorEx() callback: %s%n",cause);
            if (cause instanceof JCSMPTransportException) {  // unrecoverable, all reconnect attempts failed
                //isShutdown = true;
            } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())+": "+e.getResponsePhrase());
                System.out.println(cause);
            }
        }
    };
    

    // all plugins must provide a constructor that accepts id, Configuration, and Context
    public Solace(final String id, final Configuration config, final Context context) throws JCSMPException {
        // constructors should validate configuration options
        this.id = id;
        this.config = config;
        //prefix = config.get(PREFIX_CONFIG);
        
        for (String key : config.allKeys()) {
            //sb.append(String.format("%s:%s, ",key,config.get(key)));
            sb.append(String.format("%s, ",key));
        }
        System.out.println("AARON config: "+sb.toString());
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, config.get(HOST_CONFIG));          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME, config.get(VPN_CONFIG));     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, config.get(USERNAME_CONFIG));      // client-username
        properties.setProperty(JCSMPProperties.PASSWORD, config.get(PASSWORD_CONFIG).getPassword());      // password
        properties.setProperty(JCSMPProperties.GENERATE_SEQUENCE_NUMBERS,true);  // why not?
        properties.setProperty(JCSMPProperties.APPLICATION_DESCRIPTION,"Logstash publisher");
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES,channelProps);
        session = JCSMPFactory.onlyInstance().createSession(properties,null,sessionEventHandler);
        session.setProperty(JCSMPProperties.CLIENT_NAME,"logstash_output_"+session.getProperty(JCSMPProperties.CLIENT_NAME));
        session.connect();
        producer = session.getMessageProducer(pubHandler);
    }
    
    
/*     private enum Type {
        NUMBER,
        STRING;
    }
 */    
//    private static Map<String,Map<String,Type>> asdf = new ConcurrentHashMap<>();
    
/*     private static Type getType(Object value) {
        try {
            long l = Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(value.toString());
            } catch (NumberFormatException e2) {
                
            }
        }
        return Type.NUMBER;
    }
    
    private void parseMap(Map<String,Object> map, JsonObjectBuilder jab) {

    }
 */
    @Override
    public void output(final Collection<Event> events) {
        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        Iterator<Event> iter = events.iterator();
        try {
            while (iter.hasNext() && !stopped) {
                Event event = iter.next();
                
                JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
                
                StringBuilder sb = new StringBuilder();
                for (String key : event.getMetadata().keySet()) {
                    sb.append(key).append(':').append(event.getMetadata().get(key)).append(", ");
                }
                jsonBuilder.add("theMetadata", sb.toString());
                
                Map<String,Object> map = event.getData();
                for (String key : map.keySet()) {
                    Object val = event.getField(key);
                    if (val instanceof String) {  // most common
                        jsonBuilder.add(key,(String)val);
                    } else if (val instanceof java.lang.Long) {
                        jsonBuilder.add(key,(Long)val);
                    } else if (val instanceof java.lang.Double) {
                        jsonBuilder.add(key,(Double)val);
                    } else if (val instanceof java.lang.Boolean) {
                        jsonBuilder.add(key,(Boolean)val);
                    } else if (val instanceof org.logstash.Timestamp) {
                        Timestamp ts = (Timestamp)val;
                        jsonBuilder.add(key,ts.toString());
                    } else if (val instanceof java.util.ArrayList) {
                        ArrayList<?> list = (ArrayList<?>)val;
                        JsonArrayBuilder jab = Json.createArrayBuilder();
                        for (Object o : list) {
                            jab.add(o.toString());  // hopefully they're strings, b/c we have no idea
                        }
                        jsonBuilder.add(key,jab);
                    } else {
                        System.out.println("AARON found a non-expected type! "+key+": "+val+": "+(val==null?"null":val.getClass().getName()));
                        //jsonBuilder.add(key,String.format("%s (%s)",val,val.getClass().getName()));
                    }
                }
                JsonObjectBuilder job = Json.createObjectBuilder().add("nestedString", "here I am");
                jsonBuilder.add("nested", job);
                
                //message.setText(sb.toString()+"                                "+jsonBuilder.build().toString()+"     "+sb.toString());
                message.setText(jsonBuilder.build().toString());
                //message.setText(event.toJson());
                String topic = config.get(TOPIC_CONFIG);
                if (event.includes("[@metadata][solace-topic]")) {
                    topic += "/" +event.getMetadata().get("solace-topic");
                    //System.out.println("AARON metadata includes topic!");
                }
                // if (event.getField("solace-topic") != null) {
                //     topic = event.getField("solace-topic").toString();
                // }

                message.setSenderTimestamp(event.getEventTimestamp().toEpochMilli());
                
                
                
/*                if (event.getField("scope").equals("SYSTEM")) {
                    topic = String.format("logstash/%s/SYSTEM/%s/%s",
                        event.getField("severity_label"),
                        event.getField("logsource"),
                        event.getField("event")
                        );
                } else if (event.getField("scope").equals("VPN")) {
                    topic = String.format("logstash/%s/VPN/%s/%s/%s",
                            event.getField("severity_label"),
                            event.getField("logsource"),
                            event.getField("event"),
                            event.getField("vpn")
                            );
                } else {
                    topic = String.format("logstash/%s/CLIENT/%s/%s/%s/%s",
                            event.getField("severity_label"),
                            event.getField("logsource"),
                            event.getField("event"),
                            event.getField("vpn"),
                            event.getField("client")
                            );
                }


*/              

                
                producer.send(message,JCSMPFactory.onlyInstance().createTopic(topic));
                message.reset();
            }
        } catch (JCSMPException e) {
            System.out.println("Thrown exception during publish");
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        session.closeSession();
        stopped = true;
        done.countDown();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        session.closeSession();
        done.await();
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        // should return a list of all configuration options for this plugin
        return CONFIG_OPTIONS;
    }

    @Override
    public String getId() {
        return id;
    }
    
    
}
