package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The document library: folders, uploads, versions, the trash, and who may see and change what. */
class DocumentTest extends IntegrationTest {

    private JsonNode upload(User u, String org, String path, String name, String body, Map<String, String> params)
            throws Exception {
        var req = multipart("/api/orgs/" + org + "/documents" + path)
                .file(new MockMultipartFile("file", name, name.endsWith(".pdf") ? "application/pdf" : "text/plain",
                        body.getBytes()))
                .header("Authorization", "Bearer " + u.token());
        params.forEach(req::param);
        String text = mvc.perform(req).andReturn().getResponse().getContentAsString();
        return json.readTree(text);
    }

    private static List<String> names(JsonNode rows) {
        List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.path("name").asText()));
        return out;
    }

    private MockHttpServletResponse download(User u, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + u.token())).andReturn().getResponse();
    }

    @Test
    void members_upload_into_folders_add_versions_and_download_any_of_them() throws Exception {
        User owner = signUp("Docs Owner");
        User member = signUp("Docs Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String base = "/api/orgs/" + org + "/documents";

        String contracts = ok(call(owner, HttpMethod.POST, base + "/folders", Map.of("name", "Contracts")))
                .path("data").path("id").asText();
        String y2026 = ok(call(member, HttpMethod.POST, base + "/folders", Map.of("name", "2026", "parentId", contracts)))
                .path("data").path("id").asText();
        assertThat(code(call(owner, HttpMethod.POST, base + "/folders", Map.of("name", "contracts")))).isEqualTo("CONFLICT");

        JsonNode doc = ok(upload(member, org, "", "lease.pdf", "%PDF v1", Map.of("folderId", y2026,
                "description", "Office lease"))).path("data");
        String id = doc.path("id").asText();
        assertThat(doc.path("version").asInt()).isEqualTo(1);
        assertThat(doc.path("size").asLong()).isEqualTo(7);

        // The folder lists its contents, with the path from the top.
        JsonNode in = ok(call(owner, HttpMethod.GET, base + "?folder=" + y2026, null)).path("data");
        assertThat(in.path("path").findValuesAsText("name")).containsExactly("Contracts", "2026");
        assertThat(names(in.path("documents"))).containsExactly("lease.pdf");
        assertThat(in.path("documents").get(0).path("uploadedBy").path("name").asText()).isEqualTo("Docs Member");
        JsonNode top = ok(call(member, HttpMethod.GET, base, null)).path("data");
        assertThat(names(top.path("folders"))).containsExactly("Contracts");
        assertThat(top.path("folders").get(0).path("items").asInt()).isEqualTo(1);
        assertThat(top.path("folders").get(0).path("canManage").asBoolean()).isFalse();

        // Anyone may add a version; both stay downloadable.
        JsonNode v2 = ok(upload(owner, org, "/" + id + "/versions", "lease-signed.pdf", "%PDF v2 signed",
                Map.of("note", "Signed copy"))).path("data");
        assertThat(v2.path("version").asInt()).isEqualTo(2);
        JsonNode detail = ok(call(member, HttpMethod.GET, base + "/" + id, null)).path("data");
        assertThat(detail.path("versions").findValuesAsText("note")).contains("Signed copy");
        assertThat(detail.path("versions").get(0).path("current").asBoolean()).isTrue();
        assertThat(detail.path("activity").findValuesAsText("verb")).containsExactly("document.versioned", "document.uploaded");
        assertThat(download(member, base + "/" + id + "/download").getContentAsString()).isEqualTo("%PDF v2 signed");
        assertThat(download(member, base + "/" + id + "/versions/1/download").getContentAsString()).isEqualTo("%PDF v1");
        assertThat(download(member, base + "/" + id + "/versions/9/download").getStatus()).isEqualTo(404);

        // Rename keeps the file's extension on download; search finds it by its new name.
        ok(call(member, HttpMethod.PATCH, base + "/" + id, Map.of("name", "Office lease")));
        assertThat(download(owner, base + "/" + id + "/download").getHeader("Content-Disposition"))
                .contains("Office%20lease.pdf");
        JsonNode hits = ok(call(owner, HttpMethod.GET, base + "/search?q=LEASE", null)).path("data");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).path("path").findValuesAsText("name")).containsExactly("Contracts", "2026");

        // Move to the top; a folder can't go inside itself.
        ok(call(member, HttpMethod.POST, base + "/" + id + "/move", java.util.Collections.singletonMap("folderId", null)));
        assertThat(ok(call(owner, HttpMethod.GET, base, null)).path("data").path("documents")).hasSize(1);
        assertThat(code(call(owner, HttpMethod.POST, base + "/folders/" + contracts + "/move", Map.of("folderId", y2026))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(owner, HttpMethod.GET, base + "/folders", null)).path("data")).hasSize(2);
    }

    @Test
    void members_cannot_change_other_peoples_files_and_the_trash_restores() throws Exception {
        User owner = signUp("Trash Owner");
        User alice = signUp("Alice Member");
        User bob = signUp("Bob Member");
        String org = newOrg(owner);
        join(owner, org, alice, "MEMBER");
        join(owner, org, bob, "MEMBER");
        String base = "/api/orgs/" + org + "/documents";

        String folder = ok(call(alice, HttpMethod.POST, base + "/folders", Map.of("name", "Alice's"))).path("data")
                .path("id").asText();
        String id = ok(upload(alice, org, "", "plan.txt", "plan", Map.of("folderId", folder))).path("data").path("id").asText();
        ok(upload(bob, org, "", "bob.txt", "bob", Map.of("folderId", folder)));

        // Bob can see and download Alice's file but not rename, move or delete it.
        assertThat(download(bob, base + "/" + id + "/download").getContentAsString()).isEqualTo("plan");
        assertThat(code(call(bob, HttpMethod.PATCH, base + "/" + id, Map.of("name", "mine.txt")))).isEqualTo("FORBIDDEN");
        assertThat(code(call(bob, HttpMethod.POST, base + "/" + id + "/move", java.util.Collections.singletonMap("folderId", null))))
                .isEqualTo("FORBIDDEN");
        assertThat(code(call(bob, HttpMethod.DELETE, base + "/" + id, null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(bob, HttpMethod.DELETE, base + "/folders/" + folder, null))).isEqualTo("FORBIDDEN");
        // Alice made the folder, but Bob's file is in it: only an admin may delete it.
        assertThat(code(call(alice, HttpMethod.DELETE, base + "/folders/" + folder, null))).isEqualTo("FORBIDDEN");

        // Alice deletes her file; it is in her trash (not Bob's) and comes back.
        ok(call(alice, HttpMethod.DELETE, base + "/" + id, null));
        assertThat(code(call(bob, HttpMethod.GET, base + "/" + id, null))).isEqualTo("NOT_FOUND");
        assertThat(names(ok(call(alice, HttpMethod.GET, base + "/trash", null)).path("data")))
                .containsExactly("plan.txt");
        assertThat(ok(call(bob, HttpMethod.GET, base + "/trash", null)).path("data")).isEmpty();
        assertThat(code(call(bob, HttpMethod.POST, base + "/" + id + "/restore", null))).isEqualTo("FORBIDDEN");
        ok(call(alice, HttpMethod.POST, base + "/" + id + "/restore", null));

        // The owner (admin) deletes the folder: everything inside disappears, search too; restore brings it back.
        ok(call(owner, HttpMethod.DELETE, base + "/folders/" + folder, null));
        assertThat(ok(call(bob, HttpMethod.GET, base + "/search?q=txt", null)).path("data")).isEmpty();
        assertThat(code(call(bob, HttpMethod.GET, base + "?folder=" + folder, null))).isEqualTo("NOT_FOUND");
        JsonNode trash = ok(call(owner, HttpMethod.GET, base + "/trash", null)).path("data");
        assertThat(trash.findValuesAsText("kind")).containsExactly("FOLDER");
        ok(call(owner, HttpMethod.POST, base + "/folders/" + folder + "/restore", null));
        assertThat(ok(call(bob, HttpMethod.GET, base + "/search?q=txt", null)).path("data")).hasSize(2);
        assertThat(ok(call(owner, HttpMethod.PATCH, base + "/" + id, Map.of("name", "Admin renamed.txt"))).path("data")
                .path("name").asText()).isEqualTo("Admin renamed.txt");
    }

    @Test
    void guests_non_members_and_other_organisations_see_nothing() throws Exception {
        User owner = signUp("Iso Owner");
        User guest = signUp("Iso Guest");
        User stranger = signUp("Iso Stranger");
        String org = newOrg(owner);
        String other = newOrg(stranger);
        join(owner, org, guest, "GUEST");
        String base = "/api/orgs/" + org + "/documents";
        String id = ok(upload(owner, org, "", "secret.pdf", "%PDF secret", Map.of())).path("data").path("id").asText();
        String folder = ok(call(owner, HttpMethod.POST, base + "/folders", Map.of("name", "Board"))).path("data")
                .path("id").asText();

        // Guests: refused everywhere, and no permission to show the page.
        assertThat(code(call(guest, HttpMethod.GET, base, null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(guest, HttpMethod.GET, base + "/" + id, null))).isEqualTo("FORBIDDEN");
        assertThat(download(guest, base + "/" + id + "/download").getStatus()).isEqualTo(403);
        assertThat(code(upload(guest, org, "", "x.txt", "x", Map.of()))).isEqualTo("FORBIDDEN");
        List<Object> guestPerms = json.convertValue(ok(call(guest, HttpMethod.GET, "/api/orgs/" + org + "/context", null))
                .path("data").path("permissions"), new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() { });
        assertThat(guestPerms).doesNotContain("documents.view", "documents.upload");
        List<Object> ownerPerms = json.convertValue(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null))
                .path("data").path("permissions"), new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() { });
        assertThat(ownerPerms).contains("documents.view", "documents.upload", "documents.manage");

        // Non-members: the organisation doesn't exist for them.
        assertThat(code(call(stranger, HttpMethod.GET, base, null))).isEqualTo("NOT_FOUND");
        assertThat(download(stranger, base + "/" + id + "/download").getStatus()).isEqualTo(404);

        // Another organisation's ids don't resolve, even for its own members.
        String otherBase = "/api/orgs/" + other + "/documents";
        assertThat(code(call(stranger, HttpMethod.GET, otherBase + "/" + id, null))).isEqualTo("NOT_FOUND");
        assertThat(download(stranger, otherBase + "/" + id + "/download").getStatus()).isEqualTo(404);
        assertThat(code(call(stranger, HttpMethod.GET, otherBase + "?folder=" + folder, null))).isEqualTo("NOT_FOUND");
        assertThat(code(upload(stranger, other, "", "x.txt", "x", Map.of("folderId", folder)))).isEqualTo("NOT_FOUND");
        assertThat(code(call(stranger, HttpMethod.POST, otherBase + "/" + id + "/restore", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(stranger, HttpMethod.GET, otherBase + "/search?q=secret", null)).path("data")).isEmpty();
    }
}
