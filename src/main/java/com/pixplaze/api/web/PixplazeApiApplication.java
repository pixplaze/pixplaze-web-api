package com.pixplaze.api.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PixplazeApiApplication {
	private static ConfigurableApplicationContext context;
	private static Boolean isDevelopment = null;

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

	public static boolean isDevelopment() {
		if (isDevelopment != null) {
			return isDevelopment;
		}

		for (var profile : context.getEnvironment().getActiveProfiles()) {
			if (profile.equals("dev")) {
				return isDevelopment = true;
			}
		}

		return isDevelopment = false;
	}
}
