package org.lfps.mailboxes.util;

import java.util.regex.Pattern;

/**
 * Format validators for user-entered contact fields.
 */
public class Validators {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

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

}
