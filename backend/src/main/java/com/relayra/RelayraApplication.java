package com.relayra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RelayraApplication {

  public static void main(String[] args) {
    SpringApplication.run(RelayraApplication.class, args);
  }
}
