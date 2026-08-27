package dev.keryeshka.voxyseeu.api.addon;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The immutable wire contract of an addon.
 *
 * @param id addon/mod id; lowercase ASCII and at most 64 characters
 * @param protocolVersion positive addon-owned protocol version
 * @param direction permitted post-negotiation data direction
 * @param maximumPayloadBytes per-message limit, up to {@link AddonLimits#MAX_DATA_BYTES}
 */
public record AddonDescriptor(
        String id,
        int protocolVersion,
        AddonDirection direction,
        int maximumPayloadBytes
) {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    public AddonDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid addon id: " + id);
        }
        if (protocolVersion <= 0) {
            throw new IllegalArgumentException("Addon protocol version must be positive");
        }
        if (maximumPayloadBytes <= 0 || maximumPayloadBytes > AddonLimits.MAX_DATA_BYTES) {
            throw new IllegalArgumentException(
                    "Maximum addon payload must be between 1 and " + AddonLimits.MAX_DATA_BYTES + " bytes"
            );
        }
    }

    public static boolean isValidId(String value) {
        return value != null && ID_PATTERN.matcher(value).matches();
    }
}
