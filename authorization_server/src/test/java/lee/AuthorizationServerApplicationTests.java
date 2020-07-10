package lee;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AuthorizationServerApplication.class)
class AuthorizationServerApplicationTests {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void test1() {
        String admin = passwordEncoder.encode("admin");
        System.out.println(admin);
    }

}
