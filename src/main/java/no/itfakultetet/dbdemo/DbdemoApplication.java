package no.itfakultetet.dbdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@SpringBootApplication
public class DbdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbdemoApplication.class, args);
        @Controller
        class WebController {
            @RequestMapping(value = "/index")
            public String index() {
                return "index";
            }
        }
    }

}
