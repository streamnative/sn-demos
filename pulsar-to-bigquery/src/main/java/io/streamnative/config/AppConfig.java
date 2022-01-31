package io.streamnative.config;

public class AppConfig {
    public static final String SERVICE_HTTP_URL = "http://localhost:8080";
    public static final String SERVICE_URL ="pulsar://localhost:6650";
    public static final String STATE_STORAGE_SERVICE_URL    = "bk://localhost:4181";

    public static final String RAW_EVENTS_TOPIC         = "raw_events";
    public static final String PARSED_EVENTS_TOPIC      = "parsed_events";

    // Input File Path
    public static final String RAW_EVENTS_FILE_PATH = "/data/events.csv";
}
