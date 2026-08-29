package org.pms.silverocean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Integration test requires configured PMS and audit MySQL databases")
class SilveroceanApplicationTests {

    @Test
    void contextLoads() {
    }

}
