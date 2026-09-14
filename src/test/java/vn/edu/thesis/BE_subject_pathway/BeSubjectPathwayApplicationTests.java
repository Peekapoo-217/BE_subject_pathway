package vn.edu.thesis.BE_subject_pathway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionOfferRepository;
import vn.edu.thesis.BE_subject_pathway.repository.HighSchoolRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectGroupRepository;

/**
 * Smoke test load toan bo Spring context khong can DB thuc:
 * DataSourceAutoConfiguration bi exclude nen JPA repository khong duoc
 * tao - thay the bang mock de van verify duoc wiring
 * Controller -> Service -> Repository.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class BeSubjectPathwayApplicationTests {

	@MockitoBean
	private AdmissionCombinationRepository combinationRepository;

	@MockitoBean
	private AdmissionOfferRepository offerRepository;

	@MockitoBean
	private HighSchoolRepository highSchoolRepository;

	@MockitoBean
	private SubjectGroupRepository subjectGroupRepository;

	@MockitoBean
	private vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository subjectRepository;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	@DisplayName("Preflight OPTIONS qua SecurityFilterChain tra ve CORS header cho origin duoc phep")
	void corsPreflight_allowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/admissions/search-by-subjects")
						.header(HttpHeaders.ORIGIN, "http://localhost:4200")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:4200"));
	}

}
