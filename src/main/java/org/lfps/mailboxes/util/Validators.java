package org.lfps.mailboxes.util;

import java.util.regex.Pattern;

/**
 * Format validators for user-entered contact and address fields.
 */
public class Validators {

    /**
     * Matches an email address such as name@example.com.
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    /** Matches a ZIP code, with or without the four extra digits. */
    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");

    /** Matches a two-letter state code. */
    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Za-z]{2}$");

    /**
     * Checks whether a string looks like a valid email address.
     *
     * @param email the address to check
     * @return {@code true} if non-null and matches a basic email pattern
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Checks whether a string looks like a valid US phone number, ignoring
     * formatting characters.
     *
     * @param phone the phone number to check
     * @return {@code true} if it has 10 digits, or 11 digits with a leading 1
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        var digits = phone.replaceAll("[^0-9]", "");
        return digits.length() == 10 || (digits.length() == 11 && digits.startsWith("1"));
    }

    /**
     * Checks whether a string is a US ZIP code, either five digits or ZIP+4.
     *
     * @param zip the ZIP code to check
     * @return {@code true} for "12345" or "12345-6789", ignoring surrounding spaces
     */
    public static boolean isValidZip(String zip) {
        return zip != null && ZIP_PATTERN.matcher(zip.trim()).matches();
    }

    /**
     * Checks whether a string looks like a two-letter US state code.
     *
     * @param state the state code to check
     * @return {@code true} for two letters, such as "FL" or "ny", ignoring surrounding spaces
     */
    public static boolean isValidState(String state) {
        return state != null && STATE_PATTERN.matcher(state.trim()).matches();
    }

    /** Not used: values are checked with static methods. */
    private Validators() {
    }

}
