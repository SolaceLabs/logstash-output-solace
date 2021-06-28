package com.solace.aaron.logstash.output;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

import org.logstash.Timestamp;

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

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.Output;
import co.elastic.logstash.api.PluginConfigSpec;

// class name must match plugin name
@LogstashPlugin(name = "solace")
public class Solace implements Output {

    public static final PluginConfigSpec<String> PREFIX_CONFIG = PluginConfigSpec.stringSetting("prefix", " > ");

    
    private final String id;
    private String prefix;
    private PrintStream printer;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped = false;
    
    private final JsonBuilderFactory factory = Json.createBuilderFactory(null);
    private JCSMPSession session;
    private final XMLMessageProducer producer;
    private StringBuilder sb = new StringBuilder();
    private final SessionEventHandler asdf = new SessionEventHandler() {
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
    public Solace(final String id, final Configuration configuration, final Context context) throws JCSMPException {
        this(id, configuration, context, System.out);
    }

    Solace(final String id, final Configuration config, final Context context, OutputStream targetStream) throws JCSMPException {
        // constructors should validate configuration options
        this.id = id;
        prefix = config.get(PREFIX_CONFIG);
        
        for (String key : config.allKeys()) {
            //sb.append(String.format("%s:%s, ",key,config.get(key)));
            sb.append(String.format("%s, ",key));
        }
        System.out.println("AARON config: "+sb.toString());
        
        printer = new PrintStream(targetStream);
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, "192.168.42.35");          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  "default");     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, "default");      // client-username
        properties.setProperty(JCSMPProperties.GENERATE_SEQUENCE_NUMBERS,true);  // why not?
        properties.setProperty(JCSMPProperties.APPLICATION_DESCRIPTION,"Logstash publisher");
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES,channelProps);
        session = JCSMPFactory.onlyInstance().createSession(properties,null,asdf);
        session.setProperty(JCSMPProperties.CLIENT_NAME,"logstash_"+session.getProperty(JCSMPProperties.CLIENT_NAME));
        session.connect();
        producer = session.getMessageProducer(pubHandler);
    }
    
    
    private enum Type {
        NUMBER,
        STRING;
    }
    
//    private static Map<String,Map<String,Type>> asdf = new ConcurrentHashMap<>();
    
    private static Type getType(Object value) {
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
    

    @Override
    public void output(final Collection<Event> events) {
        TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
        Iterator<Event> z = events.iterator();
        try {
            while (z.hasNext() && !stopped) {
                Event event = z.next();
                //String s = prefix + z.next();
                // String s = prefix + event.toString();
                // printer.println(s);
                
/*                 StringBuilder sb = new StringBuilder();
                for (String key : event.getData().keySet()) {
                    sb.append(String.format("%s=%s               ",key,event.getField(key)));
                    int padding = sb.length() % 16;
                    for (int i=0;i<16-padding;i++) {
                        sb.append(' ');
                    }
                }
 */                
                JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
                for (String key : event.getData().keySet()) {
                    if (event.getField(key) instanceof String) {  // most common
                        jsonBuilder.add(key,(String)event.getField(key));
                    } else if (event.getField(key) instanceof java.lang.Long) {
                        jsonBuilder.add(key,(Long)event.getField(key));
                    } else if (event.getField(key) instanceof java.lang.Double) {
                        jsonBuilder.add(key,(Double)event.getField(key));                        
                    } else if (event.getField(key) instanceof java.lang.Boolean) {
                        jsonBuilder.add(key,(Boolean)event.getField(key));
                    } else if (event.getField(key) instanceof org.logstash.Timestamp) {
                        Timestamp ts = (Timestamp)event.getField(key);
                        message.setSenderTimestamp(ts.toEpochMilli());
                        jsonBuilder.add(key,event.getField(key).toString());
                    } else if (event.getField(key) instanceof java.util.ArrayList) {
                        ArrayList<?> list = (ArrayList<?>)event.getField(key);
                        JsonArrayBuilder jab = Json.createArrayBuilder();
                        for (Object o : list) {
                            jab.add(o.toString());  // hopefully they're strings, b/c we have no idea
                        }
                        jsonBuilder.add(key,jab);
                    } else {
                        System.out.println("AARON found a non-expected type!");
                        jsonBuilder.add(key,String.format("%s (%s)",event.getField(key),event.getField(key).getClass().getName()));
                    }
                }
                
                
                //message.setText(sb.toString()+"                                "+jsonBuilder.build().toString()+"     "+sb.toString());
                message.setText(jsonBuilder.build().toString());
                String topic = "#logstash/"+event.getField("topic");
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
        return Collections.singletonList(PREFIX_CONFIG);
    }

    @Override
    public String getId() {
        return id;
    }
    
    
}
