package org.pms.silverocean.service.geolocation;

public record GeoLocationResponse(String countryName,
                                  String countryCode,
                                  String region,
                                  String city,
                                  Double latitude,
                                  Double longitude) {
}
