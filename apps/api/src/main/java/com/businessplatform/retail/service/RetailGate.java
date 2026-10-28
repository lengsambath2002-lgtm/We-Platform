package com.businessplatform.retail.service;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleGate;

/**
 * Retail &amp; Commerce is VirtualBiz: usable only when {@code PLATFORM_VIRTUALBIZ_URL} (its web origin) is set.
 */
@Component
public class RetailGate implements ModuleGate {

    private final String url;

    public RetailGate(@Value("${platform.virtualbiz.url:}") String url) {
        String u = url == null ? "" : url.strip();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.isEmpty()) {
            URI parsed = URI.create(u);
            if (!("https".equals(parsed.getScheme()) || "http".equals(parsed.getScheme())) || parsed.getHost() == null) {
                throw new IllegalStateException("PLATFORM_VIRTUALBIZ_URL must be an http(s) origin, e.g. https://shop.example.com");
            }
        }
        this.url = u.isEmpty() ? null : u;
    }

    @Override
    public String moduleKey() {
        return Module.RETAIL;
    }

    @Override
    public boolean ready() {
        return url != null;
    }

    @Override
    public void requireReady() {
        if (!ready()) {
            throw BusinessException.field(ErrorCode.INVALID_STATE, "module",
                    "Retail & Commerce needs VirtualBiz: the server administrator must set PLATFORM_VIRTUALBIZ_URL first");
        }
    }

    /** VirtualBiz's web origin (no trailing slash), or null when not configured. */
    public String url() {
        return url;
    }
}
