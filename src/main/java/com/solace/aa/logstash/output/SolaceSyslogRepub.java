package com.solace.aa.logstash.output;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import com.solacesystems.jcsmp.JCSMPException;

// class name must match plugin name
@LogstashPlugin(name = "solace_syslog_repub")
public class SolaceSyslogRepub extends Solace {


    
    public SolaceSyslogRepub(String id, Configuration config, Context context) throws JCSMPException {
        super(id, config, context);
    }
    
    
    
    @Override
    protected String getTopic(Event event) {
        String topic = null;
        // This code below builds a custom topic for publishing when Logstash is configured to receive Solace broker logs via Syslog
        if (event.getMetadata().get("event") != null) {
            assert event.getField("scoppe") != null;
            if (event.getField("scope") != null) {
                if ("SYSTEM".equals(event.getField("scope"))) {
                    topic = String.format("solacelogs/%s/SYSTEM/%s/%s",
                        event.getField("severity_label"),
                        event.getField("logsource"),
                        event.getField("event")
                        );
                } else if (event.getField("scope").equals("VPN")) {
                    topic = String.format("solacelogs/%s/VPN/%s/%s/%s",
                            event.getField("severity_label"),
                            event.getField("logsource"),
                            event.getField("event"),
                            event.getField("vpn")
                            );
                } else {
                    topic = String.format("solacelogs/%s/CLIENT/%s/%s/%s/%s",
                            event.getField("severity_label"),
                            event.getField("logsource"),
                            event.getField("event"),
                            event.getField("vpn"),
                            event.getField("client")
                            );
                }
                return topic;
            }
        }
        return "solacelogs";
    }
    
    
}
