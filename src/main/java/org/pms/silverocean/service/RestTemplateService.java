package org.pms.silverocean.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class RestTemplateService {
    private final RestTemplate restTemplate;


    public RestTemplateService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public <R> R sendGetRequest(String url, HttpHeaders headers, Class<R> responseType) throws RestRequestException {
        return sendRequest(url, HttpMethod.GET, null, headers, responseType);
    }

    public <B, R> R sendPostRequest(String url, B body, HttpHeaders headers, Class<R> responseType) throws RestRequestException {
        return sendRequest(url, HttpMethod.POST, body, headers, responseType);
    }

    private String resolveErrorMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "third.party.error.400";
            case 401 -> "third.party.error.401";
            case 403 -> "third.party.error.403";
            case 404 -> "third.party.error.404";
            case 405 -> "third.party.error.405";
            case 408 -> "third.party.error.408";
            case 409 -> "third.party.error.409";
            case 410 -> "third.party.error.410";
            case 422 -> "third.party.error.422";
            case 429 -> "third.party.error.429";
            case 500 -> "third.party.error.500";
            case 501 -> "third.party.error.501";
            case 502 -> "third.party.error.502";
            case 503 -> "third.party.error.503";
            case 504 -> "third.party.error.504";
            default -> statusCode >= 500 ? "third.party.server.error" : "third.party.unreadable.response";
        };
    }

    private <B, R> R sendRequest(String url, HttpMethod method, B body, HttpHeaders headers, Class<R> responseType)
            throws RestRequestException {
        log.info("Sending {} request to URL: {}", method, url);

        var entity = HttpEntity.EMPTY;
        if (body != null && headers != null) {
            entity = new HttpEntity<>(body, headers);
        } else if (body != null) {
            entity = new HttpEntity<>(body);
        } else if (headers != null) {
            entity = new HttpEntity<>(headers);
        }

        try {
            ResponseEntity<R> response = restTemplate.exchange(url, method, entity, responseType);
            log.debug("Got response {} for expected type {}", response.getBody(), responseType);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            int statusCode = e.getStatusCode().value();
            log.error("Upstream service returned error status: {}, {}", statusCode, e.getResponseBodyAsString(), e);
            throw new RestRequestException(resolveErrorMessage(statusCode), statusCode, e.getResponseBodyAsString());
        }
    }
}

