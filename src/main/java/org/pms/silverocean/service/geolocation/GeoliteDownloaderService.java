package org.pms.silverocean.service.geolocation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

@Service @Slf4j
public class GeoliteDownloaderService {
    @Value("${geolite.licenseKey}")
    private String licenseKey;

    @Value("${geolite.edition:GeoLite2-City}")
    private String edition;

    @Value("${silverocean.dir}")
    private String appDir;

    @Value("${geolite.max-age:P14D}") // default: 14 days
    private Duration maxAge;
    @Value("${geolite.downloadURL:https://download.maxmind.com/app/geoip_download?edition_id=%s&license_key=%s&suffix=tar.gz}")
    private String downloadUrl;
    @Value("${geolite.enabled:true}")
    private boolean enabled;

    private Path targetFile;

    @PostConstruct
    public void init() {
        this.targetFile = Paths.get(appDir, edition + ".mmdb");
        if (enabled) {
            ensureDatabaseFresh();
        } else {
            log.info("GeoLite download is disabled for this runtime");
        }
    }

    @Scheduled(fixedDelayString = "${geolite.check-interval:PT24H}") // default: check once a day
    public void ensureDatabaseFresh() {
        if (!enabled) {
            return;
        }
        try {
            if (Files.notExists(targetFile) || isFileTooOld(targetFile, maxAge)) {
                log.info("GeoLite2 DB missing or too old, downloading new version...");
                downloadDatabase();
            } else {
                log.info("GeoLite2 DB is up-to-date.");
            }
        } catch (Exception e) {
            log.error("Failed checking/downloading GeoLite2 database", e);
        }
    }

    private boolean isFileTooOld(Path file, Duration maxAge) throws IOException {
        Instant lastModified = Files.getLastModifiedTime(file).toInstant();
        return lastModified.plus(maxAge).isBefore(Instant.now());
    }

    private void downloadDatabase() throws IOException {
        Files.createDirectories(targetFile.getParent());

        String url = String.format(downloadUrl, edition, licenseKey);

        Path tmpFile = Files.createTempFile("GeoLite2", ".tar.gz");
        log.info("Downloading GeoLite2 DB from {}", url);

        try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                throw new IOException("Failed to download GeoLite2 DB: HTTP " + conn.getResponseCode());
            }

            conn.getInputStream().transferTo(fos);
        }

        // Extract the .mmdb from tar.gz
        extractMmdbFromTarGz(tmpFile, targetFile);

        log.info("GeoLite2 DB updated at {}", targetFile);
        Files.deleteIfExists(tmpFile);
    }

    private void extractMmdbFromTarGz(Path tarGz, Path target) throws IOException {
        try (var fis = Files.newInputStream(tarGz);
             var gis = new GZIPInputStream(fis);
             var tis = new TarArchiveInputStream(gis)) {

            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".mmdb")) {
                    Files.copy(tis, target, StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
            }
        }
    }

    public Path getDatabaseFile() {
        return targetFile;
    }
}
