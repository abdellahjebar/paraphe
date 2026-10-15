package io.github.abdellahjebar.paraphe.identity.domain;

/** Passwords that are known to be common or compromised and must not be accepted. */
public interface PasswordBlocklist {

    PasswordBlocklist NONE = candidate -> false;

    /** @param candidate the password as typed; case and spaces are ignored by the comparison */
    boolean contains(String candidate);

    /** The form a password is compared in. Shared by the list loader and the lookup. */
    static String key(String password) {
        String normalized = java.text.Normalizer.normalize(password, java.text.Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(normalized.length());
        normalized.codePoints().filter(cp -> !Character.isWhitespace(cp))
                .forEach(cp -> out.appendCodePoint(Character.toLowerCase(cp)));
        return out.toString();
    }
}
