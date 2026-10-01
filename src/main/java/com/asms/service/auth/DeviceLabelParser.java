package com.asms.service.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Builds the human-readable device label shown in the device list, such as {@code Chrome 128 · Windows 10}
 * (UC-AUTH-09, {@code user_sessions.device_label}).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-26
 * @modified 2026-09-26
 */
@Component
public class DeviceLabelParser {

    private static final String UNKNOWN_FAMILY = "Other";
    private static final String SEPARATOR = " · ";

    // Loading the regex database is expensive; the parser is thread-safe and built once
    private final Parser parser = new Parser();

    /** @return the label, or {@code null} when nothing useful can be recognized */
    @Nullable
    public String parse(@Nullable String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        Client client = parser.parse(userAgent);
        String browser = describe(client.userAgent.family, client.userAgent.major);
        String os = describe(client.os.family, client.os.major);
        if (browser == null && os == null) {
            return null;
        }
        if (browser == null || os == null) {
            return browser != null ? browser : os;
        }
        return browser + SEPARATOR + os;
    }

    @Nullable
    private static String describe(@Nullable String family, @Nullable String major) {
        if (family == null || UNKNOWN_FAMILY.equals(family)) {
            return null;
        }
        return major == null ? family : family + " " + major;
    }
}
