package org.pms.silverocean.service.filestorage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pms.silverocean.common.PMSUtils;
import org.pms.silverocean.common.ResponseCode;
import org.pms.silverocean.service.PMSCustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GarageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    @Value("${garage.s3.bucket:slickhood-bucket}")
    private String bucketName;
    @Value("${garage.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();


    public GarageService(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostConstruct
    public void init() throws IOException {
        if (!bootstrapEnabled) {
            log.info("Garage resource bootstrap is disabled for this runtime");
            return;
        }
        uploadResourcesFiles();
    }

    private void uploadFile(String fileName, File file) throws IOException {
        String detectedType = Files.probeContentType(file.toPath());
        if (StringUtils.isBlank(detectedType)) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }

        if (doesFileExist(fileName)) {
            return;
        }
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(detectedType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
    }

    public void uploadFile(String path, MultipartFile multipartFile) throws IOException {
        String fileName = (path + multipartFile.getOriginalFilename()).replaceAll("\\s+", "_");
        try {
            fileName = PMSUtils.saveFile(path, multipartFile);
            uploadFile(fileName, Path.of(fileName).toFile());
        } finally {
            PMSUtils.deleteFileAndParents(fileName);
        }
    }

    /** Stores an object under an exact, server-generated key. */
    public void uploadBytes(String key, byte[] content, String contentType) {
        if (StringUtils.isBlank(key) || content == null || content.length == 0 || StringUtils.isBlank(contentType)) {
            throw new PMSCustomException(ResponseCode.UNSUPPORTED_MEDIA_TYPE);
        }
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key.startsWith("/") ? key.substring(1) : key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .serverSideEncryption("AES256")
                        .build(),
                RequestBody.fromBytes(content));
    }

    public String getPresignedUrl(String fileName) {
        if (!doesFileExist(fileName)) {
            return "";
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(60))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public List<String> listFiles(String prefix) {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix) // e.g., "1/10/5/sliderImages/"
                .build();

        ListObjectsV2Response res = s3Client.listObjectsV2(listRequest);

        return res.contents().stream()
                .map(S3Object::key)
                .toList();
    }

    public void deletePath(String prefix, boolean recursive) {
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .continuationToken(continuationToken);

            // If not recursive, we set the delimiter to stop at the next slash
            if (!recursive) {
                requestBuilder.delimiter("/");
            }

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(requestBuilder.build());

            if (!listResponse.contents().isEmpty()) {
                List<ObjectIdentifier> keysToDelete = listResponse.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .collect(Collectors.toList());

                // Execute batch delete for the files found at this level
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(keysToDelete).build())
                        .build();

                s3Client.deleteObjects(deleteRequest);
                log.info("Deleted {} files at {} (Recursive: {})", keysToDelete.size(), prefix, recursive);
            }

            continuationToken = listResponse.nextContinuationToken();
        } while (continuationToken != null);
    }

    private boolean doesFileExist(String fileName) {
        if (StringUtils.isBlank(fileName))
            return false;
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    private void uploadResourcesFiles() throws IOException {
        Resource[] resources = resolver.getResources("classpath:images/*");

        for (Resource resource : resources) {
            if (resource.getFilename() == null || resource.getFilename().isEmpty() || !resource.isReadable()) {
                continue;
            }

            String fileName = PMSUtils.systemImagesFolder + resource.getFilename();

            if (!doesFileExist(fileName)) {
                log.info("Uploading file: {}", fileName);
                String detectedType = Files.probeContentType(Path.of(resource.getURI()));
                if (StringUtils.isBlank(detectedType)) {
                    continue;
                }
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(fileName)
                                .contentType(detectedType)
                                .contentLength(resource.contentLength())
                                .build(),
                        RequestBody.fromBytes(resource.getContentAsByteArray()));
            }
        }
    }
}
