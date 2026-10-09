package vn.edu.thesis.BE_subject_pathway.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** Bao ve pham vi tai lieu OpenAPI cho toan bo endpoint nghiep vu. */
class OpenApiDocumentationTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AdmissionSearchController.class,
            HighSchoolController.class,
            SubjectController.class);

    @Test
    @DisplayName("Moi controller va endpoint deu co mo ta OpenAPI")
    void allApplicationEndpointsHaveOpenApiDocumentation() {
        int documentedEndpointCount = 0;

        for (Class<?> controller : CONTROLLERS) {
            Tag tag = controller.getAnnotation(Tag.class);
            assertNotNull(tag, () -> controller.getSimpleName() + " thieu @Tag");
            assertTrue(!tag.name().isBlank());

            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }

                documentedEndpointCount++;
                Operation operation = method.getAnnotation(Operation.class);
                ApiResponses responses = method.getAnnotation(ApiResponses.class);
                assertNotNull(operation, () -> endpointName(controller, method) + " thieu @Operation");
                assertTrue(!operation.summary().isBlank(),
                        () -> endpointName(controller, method) + " thieu summary");
                assertNotNull(responses, () -> endpointName(controller, method) + " thieu @ApiResponses");
                assertTrue(Arrays.stream(responses.value())
                                .anyMatch(response -> "200".equals(response.responseCode())),
                        () -> endpointName(controller, method) + " thieu response 200");
            }
        }

        assertEquals(6, documentedEndpointCount, "Can cap nhat test khi them endpoint moi");
    }

    private boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class);
    }

    private String endpointName(Class<?> controller, Method method) {
        return controller.getSimpleName() + "." + method.getName();
    }
}
