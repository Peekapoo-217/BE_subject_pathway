package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import vn.edu.thesis.BE_subject_pathway.dto.response.ProvinceDto;

class ProvinceCatalogServiceTest {
    @Test
    void cachesValidatedProvinceRowsAndResolvesASelectedProvinceCode() {
        int[] fetches = {0};
        ProvinceCatalogService service = new ProvinceCatalogService(() -> {
            fetches[0]++;
            return List.of(new ProvinceDto("66", "Tỉnh Đắk Lắk"));
        }, Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"), ZoneOffset.UTC));

        assertEquals("Tỉnh Đắk Lắk", service.requireProvinceName("66"));
        assertEquals(1, service.list().size());
        assertEquals(1, fetches[0]);
    }

    @Test
    void unavailableProviderReturnsServiceUnavailableWithoutAValidCache() {
        ProvinceCatalogService service = new ProvinceCatalogService(() -> {
            throw new IllegalStateException("offline");
        }, Clock.systemUTC());

        assertThrows(ResponseStatusException.class, service::list);
    }
}
