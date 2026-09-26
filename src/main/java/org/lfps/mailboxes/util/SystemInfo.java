package org.lfps.mailboxes.util;

/**
 * Reports the Java and JavaFX runtime versions in use.
 */
public class SystemInfo {

    /**
     * Returns the running JVM's version string.
     *
     * @return the JVM version
     */
    public static String javaVersion() {
        return System.getProperty("java.version");
    }

    /**
     * Returns the running JavaFX runtime's version string.
     *
     * @return the JavaFX version
     */
    public static String javafxVersion() {
        return System.getProperty("javafx.version");
    }

}