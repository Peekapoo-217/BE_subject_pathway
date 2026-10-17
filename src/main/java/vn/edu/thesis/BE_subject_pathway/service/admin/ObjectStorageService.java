package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.io.InputStream;

public interface ObjectStorageService {
    void put(String key, InputStream input, long length, String contentType);

    void delete(String key);

    boolean exists(String key);

    String presignedGet(String key);
}
