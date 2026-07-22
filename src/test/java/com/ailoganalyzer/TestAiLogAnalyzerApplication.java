package com.ailoganalyzer;

import org.springframework.boot.SpringApplication;

public class TestAiLogAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.from(AiLogAnalyzerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
