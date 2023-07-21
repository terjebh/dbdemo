package no.itfakultetet.dbdemo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@SpringBootApplication
public class DbdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbdemoApplication.class, args);

    }

}
