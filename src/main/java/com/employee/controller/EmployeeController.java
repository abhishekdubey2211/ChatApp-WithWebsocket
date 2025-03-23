package com.employee.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class EmployeeController {

    @GetMapping("/version")
    public String version(){
        return "EmployeeService v0.0.1";
    }
}
