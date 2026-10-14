package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.JsonProperty;

import vn.edu.thesis.BE_subject_pathway.dto.response.ProvinceDto;

@Service
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = true)
public class ProvinceCatalogService {
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final ParameterizedTypeReference<List<RemoteProvince>> REMOTE_LIST = new ParameterizedTypeReference<>() {};

    private record RemoteProvince(@JsonProperty("province_code") String code, String name) {}
    private record CacheEntry(List<ProvinceDto> provinces, Instant fetchedAt) {}
    @FunctionalInterface
    interface ProvinceFetcher { List<ProvinceDto> fetch(); }

    private final ProvinceFetcher fetcher;
    private final Clock clock;
    private final AtomicReference<CacheEntry> cached = new AtomicReference<>();

    @Autowired
    public ProvinceCatalogService(RestClient.Builder builder,
            @Value("${app.locations.api-url:https://34tinhthanh.com/api/provinces}") String apiUrl) {
        this(() -> {
            List<RemoteProvince> remote = builder.build().get().uri(apiUrl).retrieve().body(REMOTE_LIST);
            if (remote == null || remote.isEmpty()) {
                throw new IllegalStateException("Province provider returned no rows");
            }
            return remote.stream().map(item -> new ProvinceDto(item.code(), item.name())).toList();
        }, Clock.systemUTC());
    }

    ProvinceCatalogService(ProvinceFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    public List<ProvinceDto> list() {
        CacheEntry current = cached.get();
        if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(clock.instant())) {
            return current.provinces();
        }
        synchronized (cached) {
            current = cached.get();
            if (current != null && current.fetchedAt().plus(CACHE_TTL).isAfter(clock.instant())) {
                return current.provinces();
            }
            try {
                List<ProvinceDto> provinces = validate(fetcher.fetch());
                cached.set(new CacheEntry(provinces, clock.instant()));
                return provinces;
            } catch (RuntimeException exception) {
                if (current != null) {
                    return current.provinces();
                }
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Không tải được danh sách tỉnh/thành phố. Vui lòng thử lại sau", exception);
            }
        }
    }

    public String requireProvinceName(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn tỉnh/thành phố");
        }
        return list().stream().filter(province -> province.code().equals(code.trim()))
                .map(ProvinceDto::name).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tỉnh/thành phố đã chọn không hợp lệ"));
    }

    private List<ProvinceDto> validate(List<ProvinceDto> provinces) {
        if (provinces == null || provinces.isEmpty()
                || provinces.stream().anyMatch(item -> item == null || blank(item.code()) || blank(item.name()))
                || provinces.stream().map(ProvinceDto::code).distinct().count() != provinces.size()) {
            throw new IllegalStateException("Province provider returned invalid data");
        }
        return provinces.stream().sorted((left, right) -> left.name().compareToIgnoreCase(right.name())).toList();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
