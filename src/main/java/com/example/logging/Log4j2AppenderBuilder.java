package com.example.logging;


import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.services.s3.AmazonS3Client;

public class Log4j2AppenderBuilder extends org.apache.logging.log4j.core.appender.AbstractAppender.Builder
    implements org.apache.logging.log4j.core.util.Builder<Log4j2Appender> {

    // general properties
    @PluginBuilderAttribute
    private boolean verbose = false;

    @PluginBuilderAttribute
    private String tags;

    @PluginBuilderAttribute
    private int stagingBufferSize = 25;

    @PluginBuilderAttribute
    private int stagingBufferAge = 0;

    // S3 properties
    @PluginBuilderAttribute
    private String s3Bucket;

    @PluginBuilderAttribute
    private String s3Region;

    @PluginBuilderAttribute
    private String s3Path;

    @PluginBuilderAttribute
    private String s3AwsKey;

    @PluginBuilderAttribute
    private String s3AwsSecret;

    @PluginBuilderAttribute
    private String s3Compression;




    @Override
    @SuppressWarnings("unchecked")
    public Log4j2Appender build() {
        try {
           // String cacheName = "bpds_" + UUID.randomUUID().toString().replaceAll("-","");
        	 String cacheName = "bpds_";
         	
            LoggingEventCache<Event> cache = new LoggingEventCache<>(
                cacheName, createCacheMonitor(), createCachePublisher());
            return installFilter(new Log4j2Appender(
                    getName(), getFilter(), getLayout(),
                    true, cache));
        } catch (Exception e) {
            throw new RuntimeException("Cannot build appender due to errors", e);
        }
    }

    Log4j2Appender installFilter(Log4j2Appender appender) {
        appender.addFilter(new AbstractFilter() {
            @Override
            public Result filter(final LogEvent event) {
                // To prevent infinite looping, we filter out events from
                // the publishing thread
                Result decision = Result.NEUTRAL;
                if (LoggingEventCache.PUBLISH_THREAD_NAME.equals(event.getThreadName())) {
                    decision = Result.DENY;
                }
                return decision;
            }});
        return appender;
    }

    Optional<AmazonWebServiceClient> initS3ClientIfEnabled() {
        Optional<S3Configuration> s3 = Optional.empty();
        if ((null != s3Bucket) && (null != s3Path)) {
            S3Configuration config = new S3Configuration();
            config.setBucket(s3Bucket);
            config.setPath(s3Path);
            config.setRegion(s3Region);
            config.setAccessKey(s3AwsKey);
            config.setSecretKey(s3AwsSecret);
            s3 = Optional.of(config);
        }
        return s3.map(config ->
            new AwsClientBuilder(config.getRegion(),
                                 config.getAccessKey(),
                                 config.getSecretKey()).build(AmazonS3Client.class));
    }

  

   

    IBufferPublisher<Event> createCachePublisher() throws UnknownHostException {

        java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
        String hostName = addr.getHostName();
        BufferPublisher<Event> publisher = new BufferPublisher<Event>(hostName, parseTags(tags));

        initS3ClientIfEnabled().ifPresent(client -> {
            if (verbose) {
                System.out.println(String.format(
                    "Registering S3 publish helper -> %s:%s", s3Bucket, s3Path));
            }
            publisher.addHelper(new S3PublishHelper((AmazonS3Client)client,
                s3Bucket, s3Path, Boolean.parseBoolean(s3Compression)));
        });

      

     

        return publisher;
    }

    String[] parseTags(String tags) {
        Set<String> parsedTags = null;
        if (null != tags) {
            parsedTags = Stream.of(tags.split("[,;]"))
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
        } else {
            parsedTags = Collections.emptySet();
        }
        return parsedTags.toArray(new String[] {});
    }

    IBufferMonitor<Event> createCacheMonitor() {
        IBufferMonitor<Event> monitor = new CapacityBasedBufferMonitor<Event>(stagingBufferSize);
        if (0 < stagingBufferAge) {
            monitor = new TimePeriodBasedBufferMonitor<Event>(stagingBufferAge);
        }
        if (verbose) {
            System.out.println(String.format("Using cache monitor: %s", monitor.toString()));
        }
        return monitor;
    }
}
