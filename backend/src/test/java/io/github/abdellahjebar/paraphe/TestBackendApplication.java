package io.github.abdellahjebar.paraphe;

import org.springframework.boot.SpringApplication;

public class TestBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
