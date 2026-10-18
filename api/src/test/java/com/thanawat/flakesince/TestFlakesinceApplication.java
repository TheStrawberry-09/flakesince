package com.thanawat.flakesince;

import org.springframework.boot.SpringApplication;

public class TestFlakesinceApplication {

	public static void main(String[] args) {
		SpringApplication.from(FlakesinceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
