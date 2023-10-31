package exporter;

import org.apache.kafka.clients.consumer.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Exporter {

    private static final Logger logger = LoggerFactory.getLogger(Exporter.class);
    private static final int MAX_RETRIES = 5;

    public static void main(final String[] args) throws Exception {
        if (args.length != 3) {
            logger.error("Usage: java -jar <jar-file-path>> <config-file-path> <topic-name> <target-url>");
            System.exit(1);
        }
        
        // Topic to read from
        final String topic = args[1];

        // URL to export metrics to
        // Example: http://victoriametrics.dev.confluent:8428/api/v1/write
        final String targetUrl = args[2];

        logger.info("Reading from topic: {}, exporting to URL: {}", topic, targetUrl);

        // Load consumer configuration settings from a local file
        // Reusing the loadConfig method from the ProducerExample class
        final Properties props = loadConfig(args[0]);

        // Add additional properties to ensure ByteArrayDeserializer is used
        props.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        
        KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props);

        consumer.subscribe(Arrays.asList(topic));

        int numRetries = 0;
        while (true) {
            ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<byte[], byte[]> record : records) {
                // byte[] key = record.key();
                byte[] value = record.value();
                if (value != null) {
                    logger.info( "Consumed event {} from topic {}: valuelength = {}", record.offset(), topic, value.length);
                    
                    boolean success = false;
                    while (!success && numRetries < MAX_RETRIES) {
                        try {
                            // Set up REST API connection
                            URL url = new URL(targetUrl);

                            // Open a connection to the REST API endpoint
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);

                            // Set headers in the request
                            conn.setRequestProperty("Content-Type", "application/x-protobuf");
                            conn.setRequestProperty("User-Agent", "promkafka-0.0.1");
                            conn.setRequestProperty("X-Prometheus-Remote-Write-Version", "0.1.0");

                            // Send the byte array to the REST API endpoint
                            OutputStream os = conn.getOutputStream();
                            os.write(value);
                            os.flush();
                            // Check the response code from the REST API endpoint
                            int responseCode = conn.getResponseCode();
                            logger.info("Response code: {}", responseCode);

                            os.close();

                            // Commit the offset if the response code is 2xx
                            if (responseCode >= 200 && responseCode < 300) {
                                consumer.commitSync();
                                success = true;
                                numRetries = 0;
                            }                    
                        } catch (IOException e) {
                            logger.error("Error sending message to REST API: {}", e.getMessage());
                            numRetries++; 
                            if (numRetries < 5) { 
                                logger.info("Retrying in 30 seconds..."); Thread.sleep(30000); 
                            } 
                            else { 
                                logger.error("Max retries exceeded. Exiting application."); 
                                throw e; // Stop the application or fail when max retries exceeded 
                            }
                        }
                    }    
                }
            }
        }
    }

    // We'll reuse this function to load properties from the Consumer as well
    public static Properties loadConfig(final String configFile) throws IOException {
        if (!Files.exists(Paths.get(configFile))) {
            throw new IOException(configFile + " not found.");
        }
        final Properties cfg = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            cfg.load(inputStream);
        } catch (Exception e) { 
            logger.error("Error loading config file", e);
        }
        return cfg;
    }
}