package no.itfakultetet.dbdemo.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Index {
    @RequestMapping(value = "/index")
    public String index() {
        return "index";
    }
}

