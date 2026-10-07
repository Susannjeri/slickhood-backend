package org.pms.silverocean.service.filestorage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GarageServiceFileListCacheTest {
    @Test
    void repeatedGalleryReadsUseBoundedShortLivedKeyCache() {
        S3Client s3 = mock(S3Client.class);
        when(s3.listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .thenReturn(response("7/99/12/sliderImages/one.jpg"));
        GarageService garage = service(s3);

        assertThat(garage.listFiles("/7/99/12/sliderImages")).containsExactly("7/99/12/sliderImages/one.jpg");
        assertThat(garage.listFiles("7/99/12/sliderImages")).containsExactly("7/99/12/sliderImages/one.jpg");

        verify(s3, times(1)).listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class));
    }

    @Test
    void uploadImmediatelyInvalidatesAffectedGalleryCache() {
        S3Client s3 = mock(S3Client.class);
        when(s3.listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class)))
                .thenReturn(response("7/99/12/sliderImages/one.jpg"));
        GarageService garage = service(s3);

        garage.listFiles("7/99/12/sliderImages");
        garage.uploadBytes("7/99/12/sliderImages/two.jpg", new byte[]{1}, "image/jpeg");
        garage.listFiles("7/99/12/sliderImages");

        verify(s3, times(2)).listObjectsV2(any(software.amazon.awssdk.services.s3.model.ListObjectsV2Request.class));
    }

    private GarageService service(S3Client s3) {
        GarageService garage = new GarageService(s3, mock(S3Presigner.class), mock(UploadMalwarePolicy.class));
        ReflectionTestUtils.setField(garage, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(garage, "fileListCacheTtlSeconds", 30L);
        ReflectionTestUtils.setField(garage, "fileListCacheMaxEntries", 10);
        return garage;
    }

    private ListObjectsV2Response response(String key) {
        return ListObjectsV2Response.builder().contents(S3Object.builder().key(key).build()).build();
    }
}
