package sk.peciatka.stamps.domain;

import java.util.Map;

/** A business rule was violated (cooldown, no reward, …). Mapped to HTTP 409 with a machine-readable code. */
public class DomainException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public DomainException(String code) {
        this(code, Map.of());
    }

    public DomainException(String code, Map<String, Object> details) {
        super(code);
        this.code = code;
        this.details = details;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
