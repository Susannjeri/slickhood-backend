package org.pms.silverocean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableAsync @EnableScheduling
public class SilveroceanApplication {

    public static void main(String[] args) {
        SpringApplication.run(SilveroceanApplication.class, args);
    }

}
