package com.pixplaze.api.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PixplazeApiApplication {
	private static ConfigurableApplicationContext context;

	public static void main(String[] args) {
		 context = SpringApplication.run(PixplazeApiApplication.class, args);
	}

	@SuppressWarnings("unchecked")
    public static <T> T getBean(Class<?> beanClass) {
		return (T) context.getBean(beanClass);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getBean(String beanName) {
		return (T) context.getBean(beanName);
	}
}
