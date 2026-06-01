package com.example.medical.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final java.util.regex.Pattern UPPERCASE = java.util.regex.Pattern.compile("[A-Z]");
    private static final java.util.regex.Pattern LOWERCASE = java.util.regex.Pattern.compile("[a-z]");
    private static final java.util.regex.Pattern DIGIT = java.util.regex.Pattern.compile("[0-9]");
    private static final java.util.regex.Pattern SPECIAL = java.util.regex.Pattern.compile("[^A-Za-z0-9]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        return value.length() >= MIN_LENGTH
                && UPPERCASE.matcher(value).find()
                && LOWERCASE.matcher(value).find()
                && DIGIT.matcher(value).find()
                && SPECIAL.matcher(value).find();
    }
}
