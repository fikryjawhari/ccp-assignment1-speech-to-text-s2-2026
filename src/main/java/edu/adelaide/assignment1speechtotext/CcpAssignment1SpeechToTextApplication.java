package edu.adelaide.assignment1speechtotext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point.
 *
 * <p>{@code @SpringBootApplication} bundles three things: component scanning of this package and
 * everything below it, auto-configuration (Tomcat, Jackson, multipart parsing and the rest, chosen
 * from what is on the classpath), and marking this class as a source of bean definitions.
 *
 * <p>{@code @ConfigurationPropertiesScan} does for {@code @ConfigurationProperties} types what
 * component scanning does for {@code @Component}: it finds them and binds them at startup. Records
 * annotated {@code @ConfigurationProperties} are not {@code @Component}s, so without this they
 * would simply never be instantiated and injecting one would fail with "no qualifying bean". The
 * alternative -- listing each type in {@code @EnableConfigurationProperties} -- means editing this
 * class every time a properties type is added, so the scan is the lower-maintenance choice.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CcpAssignment1SpeechToTextApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcpAssignment1SpeechToTextApplication.class, args);
    }

}
