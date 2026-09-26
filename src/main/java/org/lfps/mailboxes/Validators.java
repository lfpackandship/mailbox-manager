package org.lfps.mailboxes;

import java.util.regex.Pattern;

public class Validators {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        var digits = phone.replaceAll("[^0-9]", "");
        return digits.length() == 10 || (digits.length() == 11 && digits.startsWith("1"));
    }

}
