package vn.edu.thesis.BE_subject_pathway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import vn.edu.thesis.BE_subject_pathway.controller.api.AdminDocumentController;
import vn.edu.thesis.BE_subject_pathway.controller.api.AdminManagementController;
import vn.edu.thesis.BE_subject_pathway.controller.api.AuthController;
import vn.edu.thesis.BE_subject_pathway.service.admin.AdminManagementService;
import vn.edu.thesis.BE_subject_pathway.service.admin.AuthService;
import vn.edu.thesis.BE_subject_pathway.service.admin.RedisSessionStore;
import vn.edu.thesis.BE_subject_pathway.service.admin.CursorCodec;
import vn.edu.thesis.BE_subject_pathway.service.admin.R2StorageService;
import vn.edu.thesis.BE_subject_pathway.service.admin.SchoolDocumentService;
import vn.edu.thesis.BE_subject_pathway.service.admin.TokenService;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AdminManagementRepository;
import vn.edu.thesis.BE_subject_pathway.repository.admin.AuthAccountRepository;
import vn.edu.thesis.BE_subject_pathway.repository.admin.SchoolDocumentRepository;

class AdminWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
            .withUserConfiguration(SchoolDocumentRepository.class, AuthAccountRepository.class,
                    AdminManagementRepository.class, SchoolDocumentService.class, AuthService.class, AdminManagementService.class,
                    AdminDocumentController.class, AuthController.class, AdminManagementController.class,
                    SuperAdminBootstrap.class)
            .withBean(RedisSessionStore.class, () -> mock(RedisSessionStore.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(R2StorageService.class, () -> mock(R2StorageService.class))
            .withBean(CursorCodec.class, () -> new CursorCodec("test-secret-with-at-least-32-bytes"))
            .withBean(TokenService.class, () -> new TokenService("test-secret-with-at-least-32-bytes", 900))
            .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new);

    private final WebApplicationContextRunner securityRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcTemplateAutoConfiguration.class))
            .withUserConfiguration(SecurityConfig.class)
            .withBean(AuthAccountRepository.class, () -> mock(AuthAccountRepository.class))
            .withBean(RedisSessionStore.class, () -> mock(RedisSessionStore.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(TokenService.class, () -> new TokenService("test-secret-with-at-least-32-bytes", 900));

    @Test
    void adminBeansExistWhenJdbcTemplateIsAutoConfigured() {
        runner.withPropertyValues("app.admin.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JdbcTemplate.class);
            assertThat(context).hasSingleBean(SchoolDocumentService.class);
            assertThat(context).hasSingleBean(AuthService.class);
            assertThat(context).hasSingleBean(AdminManagementService.class);
            assertThat(context).hasSingleBean(AdminDocumentController.class);
            assertThat(context).hasSingleBean(AuthController.class);
            assertThat(context).hasSingleBean(AdminManagementController.class);
            assertThat(context).hasSingleBean(SuperAdminBootstrap.class);
        });
    }

    @Test
    void adminBeansAreAbsentWhenAdminIsDisabled() {
        runner.withPropertyValues("app.admin.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SchoolDocumentService.class);
            assertThat(context).doesNotHaveBean(AuthService.class);
            assertThat(context).doesNotHaveBean(AdminManagementService.class);
            assertThat(context).doesNotHaveBean(AdminDocumentController.class);
            assertThat(context).doesNotHaveBean(AuthController.class);
            assertThat(context).doesNotHaveBean(AdminManagementController.class);
            assertThat(context).doesNotHaveBean(SuperAdminBootstrap.class);
        });
    }

    @Test
    void bearerFilterIsPresentInSecurityChainWhenAdminIsEnabled() {
        securityRunner.withPropertyValues("app.admin.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BearerTokenFilter.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                    .anyMatch(BearerTokenFilter.class::isInstance);
        });
    }

    @Test
    void bearerFilterIsAbsentWhenAdminIsDisabled() {
        securityRunner.withPropertyValues("app.admin.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BearerTokenFilter.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }
}
