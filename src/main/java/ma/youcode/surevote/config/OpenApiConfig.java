package ma.youcode.surevote.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI surevoteOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.surevote.ma").description("Production")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, jwtSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("SUREVOTE REST API")
                .version("1.0.0")
                .description("""
                        ## SUREVOTE — Secure Electronic Voting Platform

                        A centralized, modern, and highly secure web-based electronic voting platform.

                        ### Authentication
                        Use `POST /api/auth/login` to obtain a JWT token, then click **Authorize** \
                        and enter: `Bearer <your-token>`

                        ### Roles
                        - **ADMIN**: Full access — manage elections, candidates, colleges, users, and audit logs.
                        - **ELECTEUR**: Voter access — browse elections, submit ballots, verify receipts.
                        - **OBSERVATEUR**: Read-only access — view metrics and export audit journals.
                        """)
                .contact(new Contact()
                        .name("BRAIK Hamza")
                        .email("braik.hamza@youcode.ma"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter your JWT token. Example: `eyJhbGci...`");
    }
}
