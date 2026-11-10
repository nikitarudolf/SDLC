package by.bsuir.labqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LabQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabQueueApplication.class, args);
    }
}
