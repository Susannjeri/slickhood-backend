package org.pms.silverocean.service.geolocation;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
public class GeoLocationService {
    private final GeoliteDownloaderService geoliteDownloaderService;

    private DatabaseReader dbReader;
    @Value("${geolite.enabled:true}")
    private boolean enabled;

    public GeoLocationService(GeoliteDownloaderService geoliteDownloaderService) {
        this.geoliteDownloaderService = geoliteDownloaderService;

    }

    @PostConstruct
    private void init() throws IOException {
        if (!enabled) {
            return;
        }
        File database = geoliteDownloaderService.getDatabaseFile().toFile();
        if (!database.exists()) {
            throw new IOException("GeoLite2 database file not found. Geolocation service will not work.");
        }
        this.dbReader = new DatabaseReader.Builder(database).build();
    }

    /**
     * Retrieves the location information for a given IP address.
     *
     * @param ipAddress The IP address to look up.
     * @return A GeoLocationResponse object with the location details.
     */
    public GeoLocationResponse getLocation(String ipAddress) {
        if (dbReader == null) {
            return new GeoLocationResponse("Geolocation unavailable.", null, null, null, null, null);
        }
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            CityResponse response = dbReader.city(ip);

            Country country = response.getCountry();
            Subdivision subdivision = response.getMostSpecificSubdivision();
            City city = response.getCity();
            Location location = response.getLocation();

            return new GeoLocationResponse(
                    country.getName(),
                    country.getIsoCode(),
                    subdivision.getName(),
                    city.getName(),
                    location.getLatitude(),
                    location.getLongitude()
            );
        } catch (UnknownHostException e) {
            return new GeoLocationResponse("Invalid IP address format.", null, null, null, null, null);
        } catch (IOException | GeoIp2Exception e) {
            // Handle cases where the IP is not found in the database or other lookup errors
            return new GeoLocationResponse("IP not found in database or lookup error.", null, null, null, null, null);
        }
    }

}
