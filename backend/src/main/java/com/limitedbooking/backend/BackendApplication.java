package com.limitedbooking.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	// Boot MainClassFinder의 public static main 탐지 계약을 유지한다.
	@SuppressWarnings("UnnecessaryModifier")
	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
