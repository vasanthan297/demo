package com.example.logging;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import org.apache.http.entity.ContentType;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;

public class S3PublishHelper implements IPublishHelper<Event> {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String S3ERRCODE_BUCKETALREADYOWNEDBYYOU = "BucketAlreadyOwnedByYou";

    private final AmazonS3Client client;
    private final String bucket;
    private final String path;
    private boolean compressEnabled = false;

    private volatile boolean bucketExists = false;

    private File tempFile;
    private Writer outputWriter;


    public S3PublishHelper(AmazonS3Client client, String bucket, String path, boolean compressEnabled) {
        this.client = client;
        this.bucket = bucket.toLowerCase();
        if (!path.endsWith("/")) {
            this.path = path + "/";
        } else {
            this.path = path;
        }
        this.compressEnabled = compressEnabled;
    }

    public void start(PublishContext context) {
        try {
            tempFile = File.createTempFile("s3Publish", "bpds");
            OutputStream os = createCompressedStreamAsNecessary(
                new BufferedOutputStream(new FileOutputStream(tempFile)),
                compressEnabled);
            outputWriter = new OutputStreamWriter(os);
              System.out.println(
                  String.format("Collecting content into %s before sending to S3.", tempFile));

            if (!bucketExists) {
                bucketExists = client.doesBucketExist(bucket);
                if (!bucketExists) {
                    client.createBucket(bucket);
                    bucketExists = true;
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(String.format("Cannot start publishing: %s", ex.getMessage()), ex);
        }
    }

    public void publish(PublishContext context, int sequence, Event event) {
        try {
            outputWriter.write(event.getMessage());
            outputWriter.write(LINE_SEPARATOR);
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("Cannot collect event %s: %s", event, ex.getMessage()), ex);
        }
    }


    public void end(PublishContext context) {
        String key = String.format("%s%s", path, context.getCacheName());
		/* System.out.println(String.format("Publishing to S3 (bucket=%s; key=%s):",
			bucket, key)); */

        try {
            if (null != outputWriter) {
                outputWriter.close();
                outputWriter = null;
                // System.out.println(String.format("Publishing content of %s to S3.", tempFile));
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(tempFile.length());
                metadata.setContentType(ContentType.DEFAULT_BINARY.getMimeType());

                System.out.println("###################tempFile = " + tempFile);
                PutObjectRequest por = new PutObjectRequest(bucket, key, tempFile);
                por.setMetadata(metadata);

                PutObjectResult result = client.putObject(por);
                /* System.out.println(String.format("Content MD5: %s",
                    result.getContentMd5())); */
            }
        } catch (UnsupportedEncodingException e) {
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format("Cannot publish to S3: %s", ex.getMessage()), ex);
        } finally {
            if (null != tempFile) {
                try {
                    tempFile.delete();
                    tempFile = null;
                } catch (Exception ex) {
                }
            }
        }
    }

    static OutputStream createCompressedStreamAsNecessary(
        OutputStream outputStream, boolean compressEnabled) throws IOException {
        Objects.requireNonNull(outputStream);
        if (compressEnabled) {
            // System.out.println("Content will be compressed.");
            return new GZIPOutputStream(outputStream);
        } else {
            return outputStream;
        }
    }
}