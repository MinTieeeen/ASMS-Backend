package com.asms.service.storage;

import com.asms.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Object storage of the application on an S3-compatible service: AWS S3, or Cloudflare R2 / MinIO when
 * {@code app.storage.endpoint} is set (section 8.3 of the Module 2 spec). Shared by every module that stores files.
 *
 * <p>The application starts without storage settings; operations then fail with {@link StorageUnavailableException}.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Slf4j
@Service
public class StorageService {

    /** DeleteObjects accepts at most 1000 keys per call */
    private static final int DELETE_BATCH_SIZE = 1000;

    @Nullable
    private final S3Client client;

    @Nullable
    private final String bucket;

    @Autowired
    public StorageService(AppProperties props) {
        AppProperties.Storage storage = props.storage();
        this.bucket = StringUtils.hasText(storage.bucket()) ? storage.bucket() : null;
        this.client = bucket == null ? null : buildClient(storage);
        if (client == null) {
            log.warn("Object storage is not configured (STORAGE_BUCKET): avatar uploads are disabled");
        }
    }

    /** Overridable in tests */
    protected StorageService(@Nullable S3Client client, @Nullable String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public void put(String key, byte[] content, String contentType, String cacheControl) {
        try {
            client().putObject(
                            PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .contentType(contentType)
                                    .cacheControl(cacheControl)
                                    .build(),
                            RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageUnavailableException("Could not store " + key, e);
        }
    }

    /** Deletes the keys; keys that do not exist are ignored */
    public void delete(Collection<String> keys) {
        List<String> remaining = new ArrayList<>(keys);
        try {
            for (int from = 0; from < remaining.size(); from += DELETE_BATCH_SIZE) {
                List<ObjectIdentifier> batch =
                        remaining.subList(from, Math.min(from + DELETE_BATCH_SIZE, remaining.size())).stream()
                                .map(key -> ObjectIdentifier.builder().key(key).build())
                                .toList();
                client().deleteObjects(builder -> builder.bucket(bucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build()));
            }
        } catch (SdkException e) {
            throw new StorageUnavailableException("Could not delete " + keys.size() + " objects", e);
        }
    }

    /** Every object under the prefix, with its last modification time */
    public List<StoredObject> list(String prefix) {
        try {
            return client()
                    .listObjectsV2Paginator(builder -> builder.bucket(bucket).prefix(prefix))
                    .contents()
                    .stream()
                    .map(StorageService::toStoredObject)
                    .toList();
        } catch (SdkException e) {
            throw new StorageUnavailableException("Could not list " + prefix, e);
        }
    }

    public boolean isConfigured() {
        return client != null;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    private S3Client client() {
        if (client == null) {
            throw new StorageUnavailableException("Object storage is not configured", null);
        }
        return client;
    }

    private static StoredObject toStoredObject(S3Object object) {
        return new StoredObject(object.key(), object.lastModified());
    }

    private static S3Client buildClient(AppProperties.Storage storage) {
        S3ClientBuilder builder =
                S3Client.builder().region(Region.of(StringUtils.hasText(storage.region()) ? storage.region() : "auto"));
        if (StringUtils.hasText(storage.accessKey()) && StringUtils.hasText(storage.secretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())));
        }
        // R2 and MinIO: custom endpoint, path-style addressing
        if (StringUtils.hasText(storage.endpoint())) {
            builder.endpointOverride(URI.create(storage.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    /** An object key and when it was last written */
    public record StoredObject(String key, Instant lastModified) {}
}
