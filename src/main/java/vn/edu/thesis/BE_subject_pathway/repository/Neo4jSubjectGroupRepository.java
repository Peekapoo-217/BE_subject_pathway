package vn.edu.thesis.BE_subject_pathway.repository;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import vn.edu.thesis.BE_subject_pathway.dto.response.SubjectDto;

/** Truy van co dinh, tham so hoa cac nhom mon tu graph projection. */
@Repository
@RequiredArgsConstructor
public class Neo4jSubjectGroupRepository {

    private static final String FIND_ACADEMIC_YEARS = """
            MATCH (:HighSchool {code: $schoolCode})-[:OFFERS_GROUP]->(group:SubjectGroup)
            RETURN DISTINCT group.academic_year AS academicYear
            ORDER BY academicYear DESC
            """;

    private static final String FIND_FOUR_SUBJECT_GROUPS = """
            MATCH (:HighSchool {code: $schoolCode})-[:OFFERS_GROUP]->(group:SubjectGroup)
            WHERE group.academic_year = $academicYear
            MATCH (group)-[:INCLUDES_SUBJECT]->(subject:Subject)
            WITH group, collect(DISTINCT {code: subject.code, name: subject.name}) AS subjects
            WHERE size(subjects) = 4
            RETURN group.group_code AS groupCode,
                   group.name AS groupName,
                   group.academic_year AS academicYear,
                   subjects
            ORDER BY groupCode
            LIMIT 100
            """;

    private final Driver driver;

    @Value("${spring.neo4j.database:edugraph}")
    private String database;

    public List<String> findAcademicYears(String schoolCode) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.run(FIND_ACADEMIC_YEARS, java.util.Map.of("schoolCode", schoolCode))
                    .list(record -> record.get("academicYear").asString());
        }
    }

    public List<SubjectGroupData> findFourSubjectGroups(String schoolCode, String academicYear) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            return session.run(FIND_FOUR_SUBJECT_GROUPS,
                            java.util.Map.of("schoolCode", schoolCode, "academicYear", academicYear))
                    .list(record -> {
                        List<SubjectDto> subjects = new ArrayList<>(record.get("subjects").asList(value ->
                                new SubjectDto(value.get("code").asString(), value.get("name").asString())));
                        return new SubjectGroupData(
                                record.get("groupCode").asString(),
                                record.get("groupName").asString(),
                                record.get("academicYear").asString(),
                                subjects);
                    });
        }
    }

    public record SubjectGroupData(
            String groupCode,
            String groupName,
            String academicYear,
            List<SubjectDto> subjects) {
    }
}
