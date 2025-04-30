package de.ait.restaurantapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class RestaurantApp {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantApp.class, args);
    }

}
