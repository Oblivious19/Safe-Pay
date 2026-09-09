package com.ofss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafePayApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafePayApplication.class, args);
	}

}
