package com.businessplatform.org.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.identity.service.AttemptLimiter;
import com.businessplatform.org.domain.OrgRole;

/**
 * "Export all our data": a ZIP with one CSV per table (every row of the organisation, from every module, child rows
 * included), the people who are or were members, and the uploaded files. Tables are found in the database schema, so
 * new modules are exported without changes here. Columns holding hashes, secrets or tokens are left out. Owners only,
 * at most 3 exports an hour per organisation; each export is in the organisation's audit log.
 */
@Service
public class OrgExportService {

    private static final Pattern SECRET = Pattern.compile(".*(hash|secret|token|password).*");
    private static final Pattern FORMULA = Pattern.compile("[=+\\-@]");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final JdbcTemplate jdbc;
    private final OrgAccess access;
    private final ActivityService activity;
    private final Path files;
    private final AttemptLimiter limiter;

    public OrgExportService(JdbcTemplate jdbc, OrgAccess access, ActivityService activity, Clock clock,
            @Value("${platform.files.dir}") String dir) {
        this.jdbc = jdbc;
        this.access = access;
        this.activity = activity;
        this.files = Path.of(dir).toAbsolutePath().normalize();
        this.limiter = new AttemptLimiter(3, Duration.ofHours(1), clock);
    }

    /** Who may export, checked before the download starts; logs the export. */
    public OrgContext start(String slug) {
        OrgContext ctx = access.require(slug);
        if (ctx.role() != OrgRole.OWNER) {
            throw BusinessException.forbidden();
        }
        if (!limiter.tryAcquire(ctx.orgId().toString())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        activity.record(ctx.orgId(), "org", "organisation", ctx.orgId(), ctx.userId(), "org.exported", Map.of());
        return ctx;
    }

    /** Writes the ZIP (streamed: nothing is held in memory). */
    public void write(OrgContext ctx, OutputStream out) throws IOException {
        UUID org = ctx.orgId();
        Map<String, String> sources = sources();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(readme(ctx, sources.size()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            csv(zip, "data/organisation.csv", "SELECT " + columns("organisation") + " FROM organisation WHERE id = ?", org);
            csv(zip, "data/people.csv", "SELECT u.id, u.name, u.email, u.locale, m.role, m.status, m.created_at AS joined_at "
                    + "FROM membership m JOIN app_user u ON u.id = m.user_id WHERE m.org_id = ? ORDER BY u.name", org);
            for (Map.Entry<String, String> s : sources.entrySet()) {
                csv(zip, "data/" + s.getKey() + ".csv", s.getValue(), org);
            }
            for (Map<String, Object> f : jdbc.queryForList("SELECT id, name, storage_key FROM stored_file WHERE org_id = ?", org)) {
                Path p = files.resolve(String.valueOf(f.get("storage_key"))).normalize();
                if (!p.startsWith(files) || !Files.isRegularFile(p)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry("files/" + f.get("id") + "-" + safe(String.valueOf(f.get("name")))));
                Files.copy(p, zip);
                zip.closeEntry();
            }
        }
    }

    /**
     * Table → query for the organisation's rows: tables with an {@code org_id}, and tables whose rows belong to one
     * of those through a foreign key (approval steps, project members …).
     */
    private Map<String, String> sources() {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> owned = jdbc.queryForList("SELECT DISTINCT table_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND column_name = 'org_id' ORDER BY table_name", String.class);
        for (String t : owned) {
            out.put(t, "SELECT " + columns(t) + " FROM \"" + t + "\" WHERE org_id = ?");
        }
        List<Map<String, Object>> children = jdbc.queryForList("""
                SELECT kcu.table_name AS child, kcu.column_name AS col, ccu.table_name AS parent
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' AND ccu.column_name = 'id'
                ORDER BY kcu.table_name, kcu.column_name""");
        for (Map<String, Object> c : children) {
            String child = (String) c.get("child");
            String parent = (String) c.get("parent");
            if (out.containsKey(child) || owned.contains(child) || !owned.contains(parent)) {
                continue;
            }
            out.put(child, "SELECT " + columns(child) + " FROM \"" + child + "\" WHERE \"" + c.get("col")
                    + "\" IN (SELECT id FROM \"" + parent + "\" WHERE org_id = ?)");
        }
        return out;
    }

    private String columns(String table) {
        List<String> cols = new ArrayList<>();
        for (String c : jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND table_name = ? ORDER BY ordinal_position", String.class, table)) {
            if (!SECRET.matcher(c).matches()) {
                cols.add("\"" + c + "\"");
            }
        }
        return String.join(", ", cols);
    }

    /** One CSV (UTF-8 with a BOM, so Excel shows Khmer); skipped when the table has no rows. */
    private void csv(ZipOutputStream zip, String name, String sql, UUID org) {
        boolean[] started = {false};
        Writer w = new OutputStreamWriter(new NoClose(zip), StandardCharsets.UTF_8);
        jdbc.query(sql, rs -> {
            try {
                int n = rs.getMetaData().getColumnCount();
                if (!started[0]) {
                    started[0] = true;
                    zip.putNextEntry(new ZipEntry(name));
                    w.write('﻿');
                    for (int i = 1; i <= n; i++) {
                        w.write((i > 1 ? "," : "") + quote(rs.getMetaData().getColumnLabel(i)));
                    }
                    w.write("\r\n");
                }
                for (int i = 1; i <= n; i++) {
                    w.write((i > 1 ? "," : "") + quote(rs.getString(i)));
                }
                w.write("\r\n");
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, org);
        if (started[0]) {
            try {
                w.flush();
                zip.closeEntry();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    static String quote(String v) {
        if (v == null) {
            return "";
        }
        // A leading = + - @ would run as a formula in a spreadsheet (negative numbers excepted).
        String s = FORMULA.matcher(v).lookingAt() && !NUMBER.matcher(v).matches() ? "'" + v : v;
        boolean quoted = s.indexOf('"') >= 0 || s.indexOf(',') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        return quoted ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String safe(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static String readme(OrgContext ctx, int tables) {
        return "Business Platform: all data of " + ctx.orgName() + " (" + ctx.slug() + ")\r\n\r\n"
                + "data/organisation.csv  the organisation\r\n"
                + "data/people.csv        everyone who is or was a member, with their role\r\n"
                + "data/<table>.csv       one file per table with rows (" + tables + " tables checked); ids link the files\r\n"
                + "files/                 uploaded files, named <file id>-<name>\r\n\r\n"
                + "CSV files are UTF-8 with a header row. Passwords, tokens and other secrets are not included.\r\n";
    }

    /** Lets a writer flush into the ZIP entry without closing the ZIP. */
    private static final class NoClose extends java.io.FilterOutputStream {
        NoClose(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
