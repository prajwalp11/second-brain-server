package com.secondbrain.second_brain_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SecondBrainServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SecondBrainServerApplication.class, args);
	}

}
