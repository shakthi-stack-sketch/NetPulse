package com.netpulse.test;

import java.util.Objects;

/**
 * Lightweight, zero-dependency assertion utilities mirroring standard test frameworks.
 */
public final class Assert {

    private Assert() {}

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be true, but was false");
        }
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, null);
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message != null ? message : "Expected condition to be false, but was true");
        }
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, null);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            String prefix = message != null ? message + " -> " : "";
            throw new AssertionError(prefix + "Expected: <" + expected + ">, but was: <" + actual + ">");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, null);
    }

    public static void assertEquals(double expected, double actual, double delta, String message) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return;
        }
        if (Math.abs(expected - actual) > delta) {
            String prefix = message != null ? message + " -> " : "";
            throw new AssertionError(prefix + "Expected: <" + expected + " +/- " + delta + ">, but was: <" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, double delta) {
        assertEquals(expected, actual, delta, null);
    }

    public static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new AssertionError(message != null ? message : "Expected object to be non-null");
        }
    }

    public static void assertNotNull(Object object) {
        assertNotNull(object, null);
    }

    public static void assertNull(Object object, String message) {
        if (object != null) {
            throw new AssertionError(message != null ? message : "Expected object to be null, but was: <" + object + ">");
        }
    }

    public static void assertNull(Object object) {
        assertNull(object, null);
    }
}
