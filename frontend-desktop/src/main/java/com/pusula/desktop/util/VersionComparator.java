package com.pusula.desktop.util;

public final class VersionComparator {

    private VersionComparator() {
    }

    /** Semver-like compare: 3.0.10 &gt; 3.0.9 */
    public static int compare(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        String[] leftParts = left.trim().split("\\.");
        String[] rightParts = right.trim().split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parsePart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parsePart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        String digits = part.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
