package com.example.demo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DemoApplication {

	  private static Logger logger = LogManager.getLogger(DemoApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
	
	@GetMapping("/test")
	public String retrieveCoursesForStudent() {
		 logger.info("Starting Spring Boot application..");
		 
		 int i = 0;
			 logger.info("Starting Spring Boot application..");
		 
		 
		return "SUCCESS";
	}
}
