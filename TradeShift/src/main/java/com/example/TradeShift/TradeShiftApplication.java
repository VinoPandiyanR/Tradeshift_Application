package com.example.TradeShift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeShiftApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeShiftApplication.class, args);
	}

}
