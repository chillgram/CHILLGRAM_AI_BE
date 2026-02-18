package com.example.chillgram.common.google;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
public class GcsFileStorage implements FileStorage {

        private final Storage storage;
        private final String bucket; // expected bucket
        private final String publicBaseUrl; // e.g. https://storage.googleapis.com/<bucket>

        public GcsFileStorage(
                        Storage storage,
                        @Value("${gcs.bucket}") String bucket,
                        @Value("${gcs.publicBaseUrl}") String publicBaseUrl) {
                this.storage = storage;
                this.bucket = bucket;
                this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        }

        public String getPublicBaseUrl() {
                return publicBaseUrl;
        }

        @Override
        public Mono<StoredFile> store(FilePart filePart) {
                return store(filePart, "ads");
        }

        @Override
        public Mono<StoredFile> store(FilePart filePart, String folder) {
                String safeName = filePart.filename().replaceAll("[\\\\/\\r\\n]", "_");
                String objectName = folder + "/" + Instant.now().toEpochMilli()
                                + "_" + UUID.randomUUID().toString().replace("-", "")
                                + "_" + safeName;

                return storeFixed(filePart, objectName);
        }

        public Mono<StoredFile> storeFixed(FilePart filePart, String objectName) {
                String safeName = filePart.filename().replaceAll("[\\\\/\\r\\n]", "_");

                // If objectName does not include an extension, try to preserve original file
                // extension
                String finalObjectName = objectName;
                try {
                        int lastSlash = objectName.lastIndexOf('/');
                        int lastDot = objectName.lastIndexOf('.');
                        boolean hasExt = lastDot > lastSlash;

                        if (!hasExt) {
                                // derive extension from original filename
                                String filename = filePart.filename();
                                String ext = null;

                                if (filename != null) {
                                        int fdot = filename.lastIndexOf('.');
                                        if (fdot > -1 && fdot < filename.length() - 1) {
                                                ext = filename.substring(fdot + 1).toLowerCase();
                                        }
                                }

                                // fallback to content-type mapping
                                if (ext == null) {
                                        var ct = filePart.headers().getContentType();
                                        if (ct != null) {
                                                String subtype = ct.getSubtype();
                                                if (subtype != null) {
                                                        if (subtype.contains("svg"))
                                                                ext = "svg";
                                                        else if (subtype.contains("jpeg") || subtype.contains("jpg"))
                                                                ext = "jpg";
                                                        else if (subtype.contains("png"))
                                                                ext = "png";
                                                        else if (subtype.contains("webp"))
                                                                ext = "webp";
                                                        else
                                                                ext = subtype.replaceAll("[^a-z0-9]", "");
                                                }
                                        }
                                }

                                if (ext == null || ext.isBlank())
                                        ext = "bin";

                                if (objectName.endsWith("/"))
                                        finalObjectName = objectName + safeName;
                                // append extension if not present
                                else
                                        finalObjectName = finalObjectName + "." + ext;
                        }
                } catch (Exception ignored) {
                        finalObjectName = objectName;
                }

                String actualObjectName = finalObjectName;

                return Mono.usingWhen(
                                Mono.fromCallable(() -> Files.createTempFile("upload_", "_" + safeName))
                                                .subscribeOn(Schedulers.boundedElastic()),
                                temp -> filePart.transferTo(temp)
                                                .then(Mono.fromCallable(() -> {
                                                        BlobId blobId = BlobId.of(bucket, actualObjectName);
                                                        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                                                                        .setContentType(
                                                                                        filePart.headers()
                                                                                                        .getContentType() != null
                                                                                                                        ? filePart.headers()
                                                                                                                                        .getContentType()
                                                                                                                                        .toString()
                                                                                                                        : "application/octet-stream")
                                                                        .build();

                                                        storage.create(blobInfo, Files.readAllBytes(temp));

                                                        String url = publicBaseUrl + "/" + actualObjectName;
                                                        String gsUri = "gs://" + bucket + "/" + actualObjectName;

                                                        return new StoredFile(url, blobInfo.getContentType(), gsUri,
                                                                        Files.size(temp));
                                                }).subscribeOn(Schedulers.boundedElastic())),
                                temp -> Mono.fromRunnable(() -> {
                                        try {
                                                Files.deleteIfExists(temp);
                                        } catch (Exception ignored) {
                                        }
                                }).subscribeOn(Schedulers.boundedElastic()));
        }

        /**
         * ✅ gs:// 뿐 아니라 GCS HTTPS URL도 지원
         * - https://storage.googleapis.com/<bucket>/<object>
         * - https://<bucket>.storage.googleapis.com/<object>
         */
        public Mono<byte[]> fetchBytes(String uri) {
                return Mono.fromCallable(() -> {
                        GcsLocation loc = parseGcsLocation(uri);
                        Blob blob = storage.get(loc.bucket(), loc.object());
                        if (blob == null)
                                throw new IllegalStateException("gcs object not found: " + uri);
                        return blob.getContent();
                }).subscribeOn(Schedulers.boundedElastic());
        }

        public String toPublicUrl(String uri) {
                if (uri == null)
                        return null;
                String u = uri.trim();

                if (u.startsWith("http://") || u.startsWith("https://"))
                        return u;

                if (!u.startsWith("gs://"))
                        return u;

                String noScheme = u.substring("gs://".length());
                int slash = noScheme.indexOf('/');
                if (slash < 0)
                        return u;

                String bkt = noScheme.substring(0, slash);
                String obj = noScheme.substring(slash + 1);

                // bucket mismatch면 변환하지 않음
                if (bucket != null && !bucket.isBlank() && !bkt.equals(bucket))
                        return u;

                return publicBaseUrl + "/" + obj;
        }

        @Override
        public Mono<Void> delete(String uri) {
                return Mono.fromCallable(() -> {
                        GcsLocation loc = parseGcsLocation(uri);
                        storage.delete(BlobId.of(loc.bucket(), loc.object()));
                        return null;
                }).subscribeOn(Schedulers.boundedElastic()).then();
        }

        // =========================
        // helpers
        // =========================

        private record GcsLocation(String bucket, String object) {
        }

        private GcsLocation parseGcsLocation(String uri) {
                if (uri == null)
                        throw new IllegalArgumentException("uri is null");
                String u = uri.trim();

                if (u.startsWith("gs://")) {
                        String noScheme = u.substring("gs://".length());
                        int slash = noScheme.indexOf('/');
                        if (slash < 0)
                                throw new IllegalArgumentException("invalid gs uri: " + u);
                        String bkt = noScheme.substring(0, slash);
                        String obj = noScheme.substring(slash + 1);
                        if (obj.isBlank())
                                throw new IllegalArgumentException("invalid gs uri (empty object): " + u);
                        return new GcsLocation(bkt, obj);
                }

                if (u.startsWith("http://") || u.startsWith("https://")) {
                        URI parsed = URI.create(u);
                        String host = parsed.getHost();
                        String path = parsed.getPath();
                        if (host == null)
                                throw new IllegalArgumentException("invalid url: " + u);

                        if ("storage.googleapis.com".equals(host)) {
                                // /bucket/object
                                if (path == null || path.length() < 2)
                                        throw new IllegalArgumentException("invalid gcs url: " + u);
                                String p = path.substring(1);
                                int slash = p.indexOf('/');
                                if (slash < 0)
                                        throw new IllegalArgumentException("invalid gcs url: " + u);
                                String bkt = p.substring(0, slash);
                                String obj = p.substring(slash + 1);
                                if (obj.isBlank())
                                        throw new IllegalArgumentException("invalid gcs url (empty object): " + u);
                                return new GcsLocation(bkt, obj);
                        }

                        String suffix = ".storage.googleapis.com";
                        if (host.endsWith(suffix)) {
                                String bkt = host.substring(0, host.length() - suffix.length());
                                String obj = (path != null && path.startsWith("/")) ? path.substring(1) : path;
                                if (obj == null || obj.isBlank())
                                        throw new IllegalArgumentException("invalid gcs url (empty object): " + u);
                                return new GcsLocation(bkt, obj);
                        }

                        throw new IllegalArgumentException("unsupported url host: " + host + " (" + u + ")");
                }

                throw new IllegalArgumentException("unsupported uri: " + u);
        }

        private static String stripTrailingSlash(String s) {
                if (s == null)
                        return "";
                return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
        }
}
