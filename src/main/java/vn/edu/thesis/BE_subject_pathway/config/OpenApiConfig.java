package vn.edu.thesis.BE_subject_pathway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata chung cho tai lieu OpenAPI cua Subject Pathway API. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI subjectPathwayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Subject Pathway API")
                .version("v1")
                .description("API tra cuu nhom mon THPT, to hop xet tuyen, truong dai hoc "
                        + "va nganh dao tao phu hop.")
                .contact(new Contact().name("Thesis Project Team"))
                .license(new License().name("Internal thesis project")));
    }
}
