package com.example.chillgram;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(excludeName = {
		"org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration"
})
public class ChillgramApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChillgramApplication.class, args);
	}

}
