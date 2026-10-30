package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Education: module switch and roles (admins, teachers, members without a role), student registration with
 * guardians and numbering under concurrency, teachers limited to their classes, student attendance, assessments and
 * results computed on the server (half-up), the timetable's double-booking checks, fees with payments, a withdrawal
 * through the approval engine, the calendar, and isolation between organisations.
 */
class EducationTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

    /** Owner and Admin run the school; two teachers (members with a profile); a member and a guest without a role. */
    private record Setup(User owner, User admin, User teacherA, User teacherB, User member, User guest, String org,
            String yearId, String term1, String term2, String teacherAId, String teacherBId) {

        String edu() {
            return "/api/orgs/" + org + "/education";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("School Owner");
        User admin = signUp("School Admin");
        User a = signUp("Teacher Sophea");
        User b = signUp("Teacher Vannak");
        User member = signUp("Office Member");
        User guest = signUp("School Guest");
        String org = newOrg(owner);
        join(owner, org, admin, "ADMIN");
        join(owner, org, a, "MEMBER");
        join(owner, org, b, "MEMBER");
        join(owner, org, member, "MEMBER");
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/education", Map.of("enabled", true)));
        String edu = "/api/orgs/" + org + "/education";
        String year = ok(call(owner, HttpMethod.POST, edu + "/years", Map.of("name", "2026–2027",
                "startsOn", TODAY.minusDays(60).toString(), "endsOn", TODAY.plusDays(300).toString(), "current", true)))
                .path("data").path("id").asText();
        String t1 = ok(call(owner, HttpMethod.POST, edu + "/years/" + year + "/terms", Map.of("name", "Semester 1",
                "startsOn", TODAY.minusDays(60).toString(), "endsOn", TODAY.plusDays(30).toString()))).path("data").path("id").asText();
        String t2 = ok(call(owner, HttpMethod.POST, edu + "/years/" + year + "/terms", Map.of("name", "Semester 2",
                "startsOn", TODAY.plusDays(31).toString(), "endsOn", TODAY.plusDays(300).toString()))).path("data").path("id").asText();
        String ta = ok(call(owner, HttpMethod.POST, edu + "/teachers", Map.of("userId", a.id().toString(), "specialty", "Mathematics")))
                .path("data").path("id").asText();
        String tb = ok(call(owner, HttpMethod.POST, edu + "/teachers", Map.of("userId", b.id().toString(), "specialty", "Khmer")))
                .path("data").path("id").asText();
        return new Setup(owner, admin, a, b, member, guest, org, year, t1, t2, ta, tb);
    }

    private String grade(Setup s, String name) throws Exception {
        return ok(call(s.owner(), HttpMethod.GET, s.edu() + "/grades", null)).path("data").findParents("nameEn").stream()
                .filter(g -> g.path("nameEn").asText().equals(name)).findFirst().orElseThrow().path("id").asText();
    }

    private String subject(Setup s, String code) throws Exception {
        return ok(call(s.owner(), HttpMethod.GET, s.edu() + "/subjects", null)).path("data").findParents("code").stream()
                .filter(g -> g.path("code").asText().equals(code)).findFirst().orElseThrow().path("id").asText();
    }

    private String newClass(Setup s, String name, String grade, String homeroom) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("yearId", s.yearId(), "gradeId", grade, "name", name, "capacity", 40));
        if (homeroom != null) {
            body.put("homeroomTeacherId", homeroom);
        }
        return ok(call(s.owner(), HttpMethod.POST, s.edu() + "/classes", body)).path("data").path("summary").path("id").asText();
    }

    private static Map<String, Object> guardian(String name, String phone) {
        return Map.of("guardian", Map.of("name", name, "phone", phone), "relationship", "MOTHER", "primary", true);
    }

    private JsonNode register(Setup s, String name, String classId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("nameEn", name, "gender", "FEMALE",
                "dateOfBirth", TODAY.minusYears(12).toString(), "admittedOn", TODAY.minusDays(30).toString(),
                "guardians", List.of(guardian("Parent of " + name, "+855 12 000 111"))));
        if (classId != null) {
            body.put("classId", classId);
        }
        return call(s.owner(), HttpMethod.POST, s.edu() + "/students", body);
    }

    private String student(Setup s, String name, String classId) throws Exception {
        return ok(register(s, name, classId)).path("data").path("student").path("id").asText();
    }

    private String course(Setup s, String classId, String subjectCode, String teacherId) throws Exception {
        return ok(call(s.owner(), HttpMethod.POST, s.edu() + "/classes/" + classId + "/courses",
                Map.of("subjectId", subject(s, subjectCode), "teacherId", teacherId))).path("data").path("id").asText();
    }

    private List<String> permissions(User u, String org) throws Exception {
        List<String> out = new ArrayList<>();
        ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions").forEach(p -> out.add(p.asText()));
        return out;
    }

    private static Map<String, Object> nullScore(String studentId) {
        Map<String, Object> m = new HashMap<>();
        m.put("studentId", studentId);
        m.put("score", null);
        return m;
    }

    /** The top-level {@code name} of each item of an array. */
    private static List<String> names(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path("name").asText()));
        return out;
    }

    private static String dec(JsonNode n) {
        return n.decimalValue().stripTrailingZeros().toPlainString();
    }

    @Test
    void the_module_is_off_until_switched_on_and_only_admins_and_teachers_get_in() throws Exception {
        User owner = signUp("Off Owner");
        User member = signUp("Off Member");
        User guest = signUp("Off Guest");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        join(owner, org, guest, "GUEST");
        String edu = "/api/orgs/" + org + "/education";
        for (String path : List.of("/overview", "/students", "/classes", "/teachers", "/settings", "/invoices")) {
            assertThat(code(call(owner, HttpMethod.GET, edu + path, null))).as(path).isEqualTo("MODULE_DISABLED");
        }
        assertThat(permissions(owner, org)).noneMatch(p -> p.startsWith("education."));
        assertThat(code(call(member, HttpMethod.PUT, "/api/orgs/" + org + "/modules/education", Map.of("enabled", true))))
                .isEqualTo("FORBIDDEN");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/education", Map.of("enabled", true)));
        assertThat(permissions(owner, org)).contains("education.view", "education.manage");
        // A member without an Education role, and a guest, get nothing.
        assertThat(permissions(member, org)).noneMatch(p -> p.startsWith("education."));
        for (String path : List.of("/overview", "/students", "/classes", "/teachers", "/timetable", "/assessments")) {
            assertThat(code(call(member, HttpMethod.GET, edu + path, null))).as(path).isEqualTo("FORBIDDEN");
            assertThat(code(call(guest, HttpMethod.GET, edu + path, null))).as(path).isEqualTo("FORBIDDEN");
        }
        // Defaults appear on first use: grades 1–12 and the curriculum's subjects; pass mark 50.
        assertThat(ok(call(owner, HttpMethod.GET, edu + "/grades", null)).path("data").findValuesAsText("nameEn"))
                .contains("Grade 1", "Grade 7", "Grade 12").hasSize(12);
        assertThat(ok(call(owner, HttpMethod.GET, edu + "/subjects", null)).path("data").findValuesAsText("code"))
                .contains("KHM", "MATH", "ENG");
        assertThat(dec(ok(call(owner, HttpMethod.GET, edu + "/settings", null)).path("data").path("passMark"))).isEqualTo("50");
        // A guest can't be a teacher; a member becomes one and then sees the module (and hears about it).
        assertThat(code(call(owner, HttpMethod.POST, edu + "/teachers", Map.of("userId", guest.id().toString()))))
                .isEqualTo("VALIDATION_FAILED");
        String t = ok(call(owner, HttpMethod.POST, edu + "/teachers", Map.of("userId", member.id().toString()))).path("data").path("id").asText();
        assertThat(code(call(owner, HttpMethod.POST, edu + "/teachers", Map.of("userId", member.id().toString())))).isEqualTo("CONFLICT");
        assertThat(permissions(member, org)).contains("education.view", "education.teach").doesNotContain("education.manage");
        assertThat(code(call(member, HttpMethod.GET, edu + "/overview", null))).isEqualTo("SUCCESS");
        assertThat(code(call(member, HttpMethod.GET, edu + "/invoices", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(member, HttpMethod.PUT, edu + "/settings", Map.of("passMark", "60")))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(member, HttpMethod.GET, "/api/orgs/" + org + "/notifications", null)).path("data").path("items")
                .findValuesAsText("type")).contains("education.teacher.added");
        // Switched off again: no more access.
        ok(call(owner, HttpMethod.PUT, edu + "/teachers/" + t, Map.of("active", false)));
        assertThat(code(call(member, HttpMethod.GET, edu + "/overview", null))).isEqualTo("FORBIDDEN");
        assertThat(permissions(member, org)).noneMatch(p -> p.startsWith("education."));
    }

    @Test
    void registration_numbers_students_once_each_and_links_their_guardians() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        JsonNode first = ok(register(s, "Sok Dara", c7a)).path("data");
        assertThat(first.path("student").path("number").asText()).isEqualTo("STU-0001");
        assertThat(first.path("currentClass").path("name").asText()).isEqualTo("7A");
        assertThat(first.path("gradeName").asText()).isEqualTo("Grade 7");
        assertThat(first.path("guardians").get(0).path("primary").asBoolean()).isTrue();
        String mother = first.path("guardians").get(0).path("id").asText();

        // A sibling shares the mother (an existing guardian) and adds a father.
        Map<String, Object> sibling = new HashMap<>(Map.of("nameEn", "Sok Dalin", "nameKm", "សុខ ដាលីន", "gender", "MALE",
                "dateOfBirth", TODAY.minusYears(10).toString(), "admittedOn", TODAY.toString(), "guardians", List.of(
                        Map.of("guardianId", mother, "relationship", "MOTHER", "primary", false),
                        Map.of("guardian", Map.of("name", "Sok Vichea", "email", "vichea@example.com"), "relationship", "FATHER", "primary", true))));
        JsonNode second = ok(call(s.owner(), HttpMethod.POST, s.edu() + "/students", sibling)).path("data");
        assertThat(second.path("student").path("number").asText()).isEqualTo("STU-0002");
        assertThat(second.path("guardians").findValuesAsText("name")).containsExactly("Sok Vichea", "Parent of Sok Dara");
        JsonNode parent = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/guardians/" + mother, null)).path("data");
        assertThat(parent.path("children").findValuesAsText("number")).containsExactlyInAnyOrder("STU-0001", "STU-0002");

        // Refused: no guardian, a guardian without phone or email, born after admission, the same guardian twice.
        Map<String, Object> bad = new HashMap<>(sibling);
        bad.put("guardians", List.of());
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/students", bad))).isEqualTo("VALIDATION_FAILED");
        bad.put("guardians", List.of(Map.of("guardian", Map.of("name", "No Contact"), "relationship", "GUARDIAN", "primary", true)));
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/students", bad))).isEqualTo("VALIDATION_FAILED");
        bad.put("guardians", List.of(Map.of("guardianId", mother, "relationship", "MOTHER", "primary", true),
                Map.of("guardianId", mother, "relationship", "MOTHER", "primary", false)));
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/students", bad))).isEqualTo("VALIDATION_FAILED");
        bad.put("guardians", sibling.get("guardians"));
        bad.put("admittedOn", TODAY.minusYears(11).toString());
        bad.put("dateOfBirth", TODAY.minusYears(10).toString());
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/students", bad))).isEqualTo("VALIDATION_FAILED");
        // Teachers don't register students; the last guardian can't be removed.
        assertThat(code(call(s.teacherA(), HttpMethod.POST, s.edu() + "/students", sibling))).isEqualTo("FORBIDDEN");
        String daraId = first.path("student").path("id").asText();
        assertThat(code(call(s.owner(), HttpMethod.DELETE, s.edu() + "/students/" + daraId + "/guardians/" + mother, null)))
                .isEqualTo("INVALID_STATE");

        // Four registrations at once get four different numbers, continuing the sequence.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<JsonNode>> done = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String name = "Parallel " + i;
                done.add(pool.submit((Callable<JsonNode>) () -> register(s, name, null)));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<JsonNode> f : done) {
                numbers.add(ok(f.get()).path("data").path("student").path("number").asText());
            }
            assertThat(numbers).containsExactlyInAnyOrder("STU-0003", "STU-0004", "STU-0005", "STU-0006");
        } finally {
            pool.shutdown();
        }
        // Search by name or number; filter by class.
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students?q=stu-0002", null)).path("data").path("items")
                .findValuesAsText("nameEn")).containsExactly("Sok Dalin");
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students?classId=" + c7a, null)).path("data").path("total").asInt())
                .isEqualTo(1);
    }

    @Test
    void teachers_see_and_mark_only_their_classes_and_attendance_rates_are_computed() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String c7b = newClass(s, "7B", g7, null);
        course(s, c7b, "KHM", s.teacherBId());
        String dara = student(s, "Sok Dara", c7a);
        String lina = student(s, "Chan Lina", c7a);
        String piseth = student(s, "Keo Piseth", c7a);
        String bora = student(s, "Ly Bora", c7b);
        // The same student can't be in two classes in one year.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/classes/" + c7b + "/enrollments",
                Map.of("studentIds", List.of(dara))))).isEqualTo("CONFLICT");

        // Teacher A (homeroom 7A) sees 7A and its students only; teacher B sees 7B only.
        assertThat(names(ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/classes", null)).path("data"))).containsExactly("7A");
        assertThat(names(ok(call(s.teacherB(), HttpMethod.GET, s.edu() + "/classes", null)).path("data"))).containsExactly("7B");
        assertThat(ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/students", null)).path("data").path("items")
                .findValuesAsText("nameEn")).containsExactly("Chan Lina", "Keo Piseth", "Sok Dara");
        assertThat(code(call(s.teacherA(), HttpMethod.GET, s.edu() + "/students/" + bora, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.teacherA(), HttpMethod.GET, s.edu() + "/classes/" + c7b, null))).isEqualTo("NOT_FOUND");
        JsonNode seen = ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/students/" + dara, null)).path("data");
        assertThat(seen.path("invoices").isNull()).isTrue();
        assertThat(seen.path("balance").isNull()).isTrue();
        assertThat(seen.path("guardians").get(0).path("phone").asText()).isEqualTo("+855 12 000 111");
        // Teachers don't change classes.
        assertThat(code(call(s.teacherA(), HttpMethod.POST, s.edu() + "/classes/" + c7a + "/enrollments",
                Map.of("studentIds", List.of(bora))))).isEqualTo("FORBIDDEN");

        // Attendance: teacher A takes today's register for 7A; B can't; a future day is refused.
        String att = s.edu() + "/classes/" + c7a + "/attendance";
        Map<String, Object> today = Map.of("day", TODAY.toString(), "entries", List.of(
                Map.of("studentId", dara, "status", "PRESENT"), Map.of("studentId", lina, "status", "LATE"),
                Map.of("studentId", piseth, "status", "ABSENT", "note", "Sick")));
        JsonNode sheet = ok(call(s.teacherA(), HttpMethod.PUT, att, today)).path("data");
        assertThat(sheet.path("marked").asBoolean()).isTrue();
        assertThat(dec(sheet.path("counts").path("rate"))).isEqualTo("66.7");
        assertThat(code(call(s.teacherB(), HttpMethod.PUT, att, today))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.teacherA(), HttpMethod.PUT, att, Map.of("day", TODAY.plusDays(1).toString(),
                "entries", List.of(Map.of("studentId", dara, "status", "PRESENT")))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.teacherA(), HttpMethod.PUT, att, Map.of("day", TODAY.toString(),
                "entries", List.of(Map.of("studentId", bora, "status", "PRESENT")))))).isEqualTo("VALIDATION_FAILED");
        // Yesterday: everyone present but Piseth excused (excused days don't count).
        ok(call(s.teacherA(), HttpMethod.PUT, att, Map.of("day", TODAY.minusDays(1).toString(), "entries", List.of(
                Map.of("studentId", dara, "status", "PRESENT"), Map.of("studentId", lina, "status", "PRESENT"),
                Map.of("studentId", piseth, "status", "EXCUSED")))));
        // Taking it again changes the marks (Lina was on time after all).
        ok(call(s.teacherA(), HttpMethod.PUT, att, Map.of("day", TODAY.toString(), "entries",
                List.of(Map.of("studentId", lina, "status", "PRESENT")))));
        JsonNode summary = ok(call(s.teacherA(), HttpMethod.GET, att + "/summary?from=" + TODAY.minusDays(7) + "&to=" + TODAY, null)).path("data");
        assertThat(summary.path("days").asInt()).isEqualTo(2);
        JsonNode total = summary.path("total");
        assertThat(total.path("present").asInt()).isEqualTo(4);
        assertThat(total.path("absent").asInt()).isEqualTo(1);
        assertThat(total.path("excused").asInt()).isEqualTo(1);
        assertThat(dec(total.path("rate"))).isEqualTo("80"); // 4 of 5 counted days
        JsonNode pisethRow = summary.path("rows").findParents("student").stream()
                .filter(r -> r.path("student").path("id").asText().equals(piseth)).findFirst().orElseThrow();
        assertThat(dec(pisethRow.path("counts").path("rate"))).isEqualTo("0");
        // The overview of teacher A counts today's register.
        JsonNode home = ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/overview", null)).path("data");
        assertThat(home.path("admin").asBoolean()).isFalse();
        assertThat(home.path("activeStudents").asInt()).isEqualTo(3);
        assertThat(home.path("classesMarkedToday").asInt()).isEqualTo(1);
        assertThat(home.path("outstanding").isNull()).isTrue();
        // Admin overview: 7B isn't marked yet today.
        JsonNode adminHome = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/overview", null)).path("data");
        assertThat(adminHome.path("activeStudents").asInt()).isEqualTo(4);
        if (TODAY.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            assertThat(names(adminHome.path("unmarkedToday"))).containsExactly("7B");
        }
    }

    @Test
    void results_are_computed_on_the_server_rounding_half_up_with_ranks_and_a_report_card() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String math = course(s, c7a, "MATH", s.teacherAId());
        String khmer = course(s, c7a, "KHM", s.teacherBId());
        String dara = student(s, "Sok Dara", c7a);
        String lina = student(s, "Chan Lina", c7a);
        String piseth = student(s, "Keo Piseth", c7a);

        Map<String, Object> exam = new HashMap<>(Map.of("termId", s.term1(), "name", "Midterm", "kind", "EXAM",
                "heldOn", TODAY.toString(), "maxScore", "80", "weight", "100"));
        String mathExam = ok(call(s.teacherA(), HttpMethod.POST, s.edu() + "/courses/" + math + "/assessments", exam)).path("data").path("id").asText();
        // Teacher B can't add assessments to A's course; weights over 100 in a term are refused; a date outside the term too.
        assertThat(code(call(s.teacherB(), HttpMethod.POST, s.edu() + "/courses/" + math + "/assessments", exam))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.teacherA(), HttpMethod.POST, s.edu() + "/courses/" + math + "/assessments", exam))).isEqualTo("VALIDATION_FAILED");
        Map<String, Object> outside = new HashMap<>(exam);
        outside.put("termId", s.term2());
        assertThat(code(call(s.teacherA(), HttpMethod.POST, s.edu() + "/courses/" + math + "/assessments", outside))).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> quiz = new HashMap<>(Map.of("termId", s.term1(), "name", "Quiz 1", "kind", "QUIZ",
                "heldOn", TODAY.toString(), "maxScore", "20", "weight", "40"));
        String kQuiz = ok(call(s.teacherB(), HttpMethod.POST, s.edu() + "/courses/" + khmer + "/assessments", quiz)).path("data").path("id").asText();
        Map<String, Object> final60 = new HashMap<>(quiz);
        final60.put("name", "Final");
        final60.put("kind", "EXAM");
        final60.put("maxScore", "50");
        final60.put("weight", "60");
        String kFinal = ok(call(s.teacherB(), HttpMethod.POST, s.edu() + "/courses/" + khmer + "/assessments", final60)).path("data").path("id").asText();

        // Scores: above the maximum and other teachers' courses are refused.
        assertThat(code(call(s.teacherA(), HttpMethod.PUT, s.edu() + "/assessments/" + mathExam + "/scores",
                Map.of("scores", List.of(Map.of("studentId", dara, "score", "81")))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.teacherA(), HttpMethod.PUT, s.edu() + "/assessments/" + kQuiz + "/scores",
                Map.of("scores", List.of(Map.of("studentId", dara, "score", "10")))))).isEqualTo("FORBIDDEN");
        // Math: Dara 65/80 = 81.25 → 81.3 (half-up); Lina 65/80 too; Piseth 30/80 = 37.5.
        JsonNode book = ok(call(s.teacherA(), HttpMethod.PUT, s.edu() + "/assessments/" + mathExam + "/scores", Map.of("scores", List.of(
                Map.of("studentId", dara, "score", "65"), Map.of("studentId", lina, "score", "65"),
                Map.of("studentId", piseth, "score", "30"))))).path("data");
        Map<String, String> mathResults = new HashMap<>();
        book.path("rows").forEach(r -> mathResults.put(r.path("student").path("id").asText(), dec(r.path("result"))));
        assertThat(mathResults).containsEntry(dara, "81.3").containsEntry(piseth, "37.5");
        // Khmer: Dara quiz 13/20 (×40 = 26) + final 37/50 (×60 = 44.4) = 70.4; Lina only the quiz so far: 17/20 = 85.
        ok(call(s.teacherB(), HttpMethod.PUT, s.edu() + "/assessments/" + kQuiz + "/scores", Map.of("scores", List.of(
                Map.of("studentId", dara, "score", "13"), Map.of("studentId", lina, "score", "17")))));
        JsonNode kBook = ok(call(s.teacherB(), HttpMethod.PUT, s.edu() + "/assessments/" + kFinal + "/scores", Map.of("scores", List.of(
                Map.of("studentId", dara, "score", "37"))))).path("data");
        assertThat(dec(kBook.path("totalWeight"))).isEqualTo("100");
        JsonNode daraK = kBook.path("rows").findParents("student").stream().filter(r -> r.path("student").path("id").asText().equals(dara)).findFirst().orElseThrow();
        assertThat(dec(daraK.path("result"))).isEqualTo("70.4");
        assertThat(daraK.path("letter").asText()).isEqualTo("C");

        // Class results: Dara (81.3 + 70.4) / 2 = 75.85 → 75.9; Lina (81.3 + 85) / 2 = 83.15 → 83.2; Piseth 37.5 (fails).
        JsonNode results = ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/classes/" + c7a + "/results?termId=" + s.term1(), null)).path("data");
        Map<String, JsonNode> rows = new HashMap<>();
        results.path("rows").forEach(r -> rows.put(r.path("student").path("id").asText(), r));
        assertThat(dec(rows.get(dara).path("average"))).isEqualTo("75.9");
        assertThat(dec(rows.get(lina).path("average"))).isEqualTo("83.2");
        assertThat(rows.get(lina).path("rank").asInt()).isEqualTo(1);
        assertThat(rows.get(dara).path("rank").asInt()).isEqualTo(2);
        assertThat(rows.get(piseth).path("rank").asInt()).isEqualTo(3);
        assertThat(rows.get(piseth).path("passed").asBoolean()).isFalse();
        assertThat(rows.get(piseth).path("letter").asText()).isEqualTo("F");
        // Clearing a score takes it out of the result.
        ok(call(s.teacherB(), HttpMethod.PUT, s.edu() + "/assessments/" + kFinal + "/scores",
                Map.of("scores", List.of(nullScore(dara)))));

        // The report card: school, class, homeroom teacher, each subject and the average, rank and attendance.
        ok(call(s.owner(), HttpMethod.PUT, s.edu() + "/settings", Map.of("schoolName", "Hope Secondary School",
                "principalName", "Mrs. Chea Sopheap", "passMark", "50")));
        JsonNode card = ok(call(s.teacherA(), HttpMethod.GET, s.edu() + "/students/" + dara + "/report-card?termId=" + s.term1(), null)).path("data");
        assertThat(card.path("school").path("schoolName").asText()).isEqualTo("Hope Secondary School");
        assertThat(card.path("classRef").path("name").asText()).isEqualTo("7A");
        assertThat(card.path("homeroom").asText()).isEqualTo("Teacher Sophea");
        assertThat(card.path("lines").findValuesAsText("letter")).contains("B");
        // With the final cleared, Khmer is the quiz only (13/20 = 65.0): (81.3 + 65.0) / 2 = 73.15 → 73.2.
        assertThat(dec(card.path("average"))).isEqualTo("73.2");
        assertThat(card.path("lines").findValuesAsText("letter")).containsExactlyInAnyOrder("B", "D");
        assertThat(card.path("classSize").asInt()).isEqualTo(3);
    }

    @Test
    void the_timetable_refuses_double_bookings_of_a_class_a_teacher_or_a_room() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String c7b = newClass(s, "7B", g7, null);
        String mathA = course(s, c7a, "MATH", s.teacherAId());
        String khmerA = course(s, c7a, "KHM", s.teacherBId());
        String mathB = course(s, c7b, "MATH", s.teacherAId());
        String khmerB = course(s, c7b, "KHM", s.teacherBId());
        String tt = s.edu() + "/timetable";
        ok(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", mathA, "weekday", 1, "startsAt", "07:00", "endsAt", "08:00", "room", "R101")));
        // 7A already has a lesson then.
        assertThat(code(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", khmerA, "weekday", 1, "startsAt", "07:30", "endsAt", "08:30"))))
                .isEqualTo("CONFLICT");
        // Teacher A is in 7A then.
        JsonNode busy = call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", mathB, "weekday", 1, "startsAt", "07:00", "endsAt", "08:00"));
        assertThat(code(busy)).isEqualTo("CONFLICT");
        assertThat(busy.path("error").path("fields").get(0).path("message").asText()).isEqualTo("This teacher already teaches another class at this time");
        // Room R101 is taken then.
        assertThat(code(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", khmerB, "weekday", 1, "startsAt", "07:15", "endsAt", "07:45", "room", "r101"))))
                .isEqualTo("CONFLICT");
        // Back to back is fine; another day is fine.
        ok(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", khmerA, "weekday", 1, "startsAt", "08:00", "endsAt", "09:00", "room", "R101")));
        ok(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", mathB, "weekday", 2, "startsAt", "07:00", "endsAt", "08:00")));
        ok(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", khmerB, "weekday", 1, "startsAt", "07:00", "endsAt", "08:00")));
        // Giving 7B's Khmer to teacher A would put them in two places on Monday 07:00.
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.edu() + "/courses/" + khmerB, Map.of("teacherId", s.teacherAId()))))
                .isEqualTo("CONFLICT");
        // Teachers read their timetable; they don't plan it.
        assertThat(ok(call(s.teacherA(), HttpMethod.GET, tt, null)).path("data").size()).isEqualTo(2);
        assertThat(ok(call(s.teacherA(), HttpMethod.GET, tt + "?classId=" + c7a, null)).path("data").size()).isEqualTo(2);
        assertThat(code(call(s.teacherA(), HttpMethod.GET, tt + "?teacherId=" + s.teacherBId(), null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.teacherA(), HttpMethod.POST, tt, Map.of("courseId", mathA, "weekday", 3, "startsAt", "07:00", "endsAt", "08:00"))))
                .isEqualTo("FORBIDDEN");
        assertThat(code(call(s.owner(), HttpMethod.POST, tt, Map.of("courseId", mathA, "weekday", 3, "startsAt", "09:00", "endsAt", "08:00"))))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void fees_are_billed_per_grade_paid_in_parts_and_due_dates_reach_the_calendar() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String g8 = grade(s, "Grade 8");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String c8a = newClass(s, "8A", g8, null);
        String dara = student(s, "Sok Dara", c7a);
        student(s, "Chan Lina", c7a);
        student(s, "Ly Bora", c8a);
        String item = ok(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items", Map.of("yearId", s.yearId(), "gradeId", g7,
                "nameEn", "Tuition semester 1", "nameKm", "ថ្លៃសិក្សាឆមាសទី១", "amount", "120.50", "dueOn", TODAY.plusDays(10).toString())))
                .path("data").path("id").asText();
        assertThat(code(call(s.teacherA(), HttpMethod.GET, s.edu() + "/fee-items", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items", Map.of("yearId", s.yearId(), "nameEn", "Bad",
                "amount", "1.005", "dueOn", TODAY.toString())))).isEqualTo("VALIDATION_FAILED");

        // Billing the Grade 7 fee makes one invoice per Grade 7 student; billing again adds none.
        JsonNode billed = ok(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items/" + item + "/bill", null)).path("data");
        assertThat(billed.path("created").asInt()).isEqualTo(2);
        assertThat(ok(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items/" + item + "/bill", null)).path("data").path("created").asInt()).isZero();
        JsonNode list = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/invoices?studentId=" + dara, null)).path("data").path("items");
        assertThat(list.size()).isEqualTo(1);
        String inv = list.get(0).path("id").asText();
        assertThat(list.get(0).path("number").asText()).startsWith("FEE-000");
        assertThat(code(call(s.teacherA(), HttpMethod.GET, s.edu() + "/invoices/" + inv, null))).isEqualTo("FORBIDDEN");

        // Pay $50 by KHQR, then the rest; more than the balance is refused.
        String pay = s.edu() + "/invoices/" + inv + "/payments";
        JsonNode part = ok(call(s.owner(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "50.00", "method", "KHQR"))).path("data");
        assertThat(part.path("summary").path("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(dec(part.path("summary").path("balance"))).isEqualTo("70.5");
        assertThat(code(call(s.owner(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "70.51", "method", "CASH"))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/invoices/" + inv + "/void", Map.of("reason", "Mistake"))))
                .isEqualTo("INVALID_STATE");
        JsonNode paid = ok(call(s.owner(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "70.50", "method", "CASH"))).path("data");
        assertThat(paid.path("summary").path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("payments").size()).isEqualTo(2);
        // Balances: the student owes nothing; the school is owed Lina's $120.50.
        assertThat(dec(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students/" + dara, null)).path("data").path("balance"))).isEqualTo("0");
        assertThat(dec(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/overview", null)).path("data").path("outstanding"))).isEqualTo("120.5");
        JsonNode report = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/reports", null)).path("data");
        JsonNode g7Fees = report.path("fees").get(0);
        assertThat(g7Fees.path("gradeName").asText()).isEqualTo("Grade 7");
        assertThat(dec(g7Fees.path("billed"))).isEqualTo("241");
        assertThat(dec(g7Fees.path("collected"))).isEqualTo("120.5");
        assertThat(report.path("attendance").findValuesAsText("name")).containsExactly("7A", "8A");

        // A one-off invoice for Ly Bora (free text), voided before anything is paid.
        String one = ok(call(s.owner(), HttpMethod.POST, s.edu() + "/invoices", Map.of("studentId",
                ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students?q=Bora", null)).path("data").path("items").get(0).path("id").asText(),
                "description", "School uniform", "amount", "15.00"))).path("data").path("summary").path("id").asText();
        assertThat(ok(call(s.owner(), HttpMethod.POST, s.edu() + "/invoices/" + one + "/void", Map.of("reason", "Paid in cash elsewhere")))
                .path("data").path("summary").path("status").asText()).isEqualTo("VOID");

        // The fee's due date is on the admins' calendar, not on a teacher's.
        String cal = "/api/orgs/" + s.org() + "/calendar?from=" + TODAY + "&to=" + TODAY.plusDays(20);
        assertThat(ok(call(s.owner(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("title"))
                .contains("Tuition semester 1 · Grade 7");
        assertThat(ok(call(s.teacherA(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("title"))
                .doesNotContain("Tuition semester 1 · Grade 7");
    }

    @Test
    void a_withdrawal_goes_through_the_approval_engine_and_the_student_leaves_the_class() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String c7b = newClass(s, "7B", g7, s.teacherBId());
        String dara = student(s, "Sok Dara", c7a);
        String bora = student(s, "Ly Bora", c7b);
        Map<String, Object> req = Map.of("effectiveOn", TODAY.toString(), "reason", "Family moved to Siem Reap");
        // Only the homeroom teacher (or an admin) asks; B isn't Dara's homeroom teacher.
        assertThat(code(call(s.teacherB(), HttpMethod.POST, s.edu() + "/students/" + dara + "/withdrawal", req))).isEqualTo("NOT_FOUND");
        JsonNode w = ok(call(s.teacherA(), HttpMethod.POST, s.edu() + "/students/" + dara + "/withdrawal", req)).path("data");
        assertThat(w.path("status").asText()).isEqualTo("PENDING");
        assertThat(w.path("approvalStatus").asText()).isEqualTo("PENDING");
        assertThat(code(call(s.teacherA(), HttpMethod.POST, s.edu() + "/students/" + dara + "/withdrawal", req))).isEqualTo("INVALID_STATE");
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/workflows", null)).path("data")
                .findValuesAsText("name")).contains("Student withdrawal");
        JsonNode inbox = ok(call(s.admin(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data");
        assertThat(inbox.findValuesAsText("title")).contains("Withdrawal · Sok Dara (STU-0001)");
        // A teacher can't decide; an admin approves.
        String approval = w.path("approvalId").asText();
        assertThat(code(call(s.teacherB(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve", Map.of())))
                .isIn("FORBIDDEN", "NOT_FOUND");
        ok(call(s.admin(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve", Map.of()));
        JsonNode after = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students/" + dara, null)).path("data");
        assertThat(after.path("student").path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(after.path("student").path("leaveReason").asText()).isEqualTo("Family moved to Siem Reap");
        assertThat(after.path("withdrawals").get(0).path("status").asText()).isEqualTo("APPROVED");
        assertThat(after.path("canRequestWithdrawal").asBoolean()).isFalse();
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/classes/" + c7a, null)).path("data").path("students").size()).isZero();
        assertThat(ok(call(s.teacherA(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data").path("items")
                .findValuesAsText("type")).contains("education.student.withdrawn");
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.edu() + "/students/" + dara + "/activity", null)).path("data")
                .findValuesAsText("verb")).contains("education.student.registered", "education.withdrawal.requested", "education.student.withdrawn");

        // Rejected: Bora stays active; the teacher may ask again.
        String other = ok(call(s.teacherB(), HttpMethod.POST, s.edu() + "/students/" + bora + "/withdrawal", req)).path("data").path("approvalId").asText();
        ok(call(s.owner(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + other + "/reject", Map.of("comment", "Talk to the family first")));
        JsonNode stays = ok(call(s.teacherB(), HttpMethod.GET, s.edu() + "/students/" + bora, null)).path("data");
        assertThat(stays.path("student").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(stays.path("withdrawals").get(0).path("status").asText()).isEqualTo("REJECTED");
        assertThat(stays.path("canRequestWithdrawal").asBoolean()).isTrue();
        // Graduation is for admins, of active students.
        assertThat(code(call(s.teacherB(), HttpMethod.POST, s.edu() + "/students/" + bora + "/graduate", Map.of("leftOn", TODAY.toString()))))
                .isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.owner(), HttpMethod.POST, s.edu() + "/students/" + bora + "/graduate", Map.of("leftOn", TODAY.toString())))
                .path("data").path("student").path("status").asText()).isEqualTo("GRADUATED");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.edu() + "/students/" + dara + "/graduate", Map.of("leftOn", TODAY.toString()))))
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void another_organisation_never_reaches_this_school() throws Exception {
        Setup s = setup();
        String g7 = grade(s, "Grade 7");
        String c7a = newClass(s, "7A", g7, s.teacherAId());
        String dara = student(s, "Sok Dara", c7a);
        String item = ok(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items", Map.of("yearId", s.yearId(), "nameEn", "Books",
                "amount", "10.00", "dueOn", TODAY.plusDays(5).toString()))).path("data").path("id").asText();
        ok(call(s.owner(), HttpMethod.POST, s.edu() + "/fee-items/" + item + "/bill", null));
        String inv = ok(call(s.owner(), HttpMethod.GET, s.edu() + "/invoices", null)).path("data").path("items").get(0).path("id").asText();

        User outsider = signUp("Other School");
        String theirs = newOrg(outsider);
        ok(call(outsider, HttpMethod.PUT, "/api/orgs/" + theirs + "/modules/education", Map.of("enabled", true)));
        String their = "/api/orgs/" + theirs + "/education";
        // Through their own organisation, this school's ids don't exist.
        for (String path : List.of("/students/" + dara, "/classes/" + c7a, "/invoices/" + inv, "/students/" + dara + "/report-card?termId=" + s.term1(),
                "/classes/" + c7a + "/attendance?day=" + TODAY)) {
            assertThat(code(call(outsider, HttpMethod.GET, their + path, null))).as(path).isEqualTo("NOT_FOUND");
        }
        assertThat(code(call(outsider, HttpMethod.POST, their + "/fee-items/" + item + "/bill", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(outsider, HttpMethod.POST, their + "/classes", Map.of("yearId", s.yearId(), "gradeId", g7, "name", "X"))))
                .isEqualTo("NOT_FOUND");
        assertThat(ok(call(outsider, HttpMethod.GET, their + "/students", null)).path("data").path("total").asInt()).isZero();
        // Directly, the school isn't there for them.
        assertThat(code(call(outsider, HttpMethod.GET, s.edu() + "/students/" + dara, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(outsider, HttpMethod.GET, s.edu() + "/overview", null))).isEqualTo("NOT_FOUND");
    }
}
