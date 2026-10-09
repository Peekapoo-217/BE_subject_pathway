package vn.edu.thesis.BE_subject_pathway.exception;

/** Dau vao xep hang tham chieu den ma mon khong thuoc du lieu truong/nam hoc. */
public class InvalidRecommendationRequestException extends RuntimeException {

    public InvalidRecommendationRequestException(String message) {
        super(message);
    }
}
