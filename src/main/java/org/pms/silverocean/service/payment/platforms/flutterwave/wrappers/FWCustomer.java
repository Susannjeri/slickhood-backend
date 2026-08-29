package org.pms.silverocean.service.payment.platforms.flutterwave.wrappers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL) @JsonIgnoreProperties(ignoreUnknown = true)
public record FWCustomer(long id,
                         String name,
                         @JsonProperty("phone_number") String phoneNumber,
                         String email,
                         @JsonProperty("created_at") String createdAt) {
    public FWCustomer(String email) {
        this(0, null, null, email, null);
    }
    public String toString() {
        return "{" +
                "\"id\":" + id +"," +
                "\"name\":\"" + name + "\"," +
                "\"phone_number\":\"hidden\"," +
                "\"email\":\"hidden\"," +
                "\"created_at\":\"" + createdAt + "\"" +
                "}";
    }
}
