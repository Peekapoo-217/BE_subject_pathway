package vn.edu.thesis.BE_subject_pathway.exception;

/**
 * Nem ra khi khong tim thay du lieu nghiep vu.
 * Duoc GlobalExceptionHandler bat va tra ve HTTP 404 (rule
 * global-exception-handling: khong try-catch roi rac trong controller/service).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(String.format("%s khong tim thay voi gia tri: %s", resourceName, identifier));
    }
}
