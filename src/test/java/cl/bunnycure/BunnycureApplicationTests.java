package cl.bunnycure;

import cl.bunnycure.config.TestMailConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestMailConfiguration.class)
class BunnycureApplicationTests {

    @Test
    void contextLoads() {
    }

}
