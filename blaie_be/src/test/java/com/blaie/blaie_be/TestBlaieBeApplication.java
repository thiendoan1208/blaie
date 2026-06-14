package com.blaie.blaie_be;

import org.springframework.boot.SpringApplication;

public class TestBlaieBeApplication {

	public static void main(String[] args) {
		SpringApplication.from(BlaieBeApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
