package com.appointment.slot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SlotApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlotApplication.class, args);
	}

}
