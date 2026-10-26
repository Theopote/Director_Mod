package com.director.compatibility;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModCompatibilityCheckerTest {
    @Test void fabricBuildMetadataDoesNotTriggerFalseIncompatibility() {
        assertTrue(ModCompatibilityChecker.isVersionCompatible("0.138.4+1.21.10", "0.138.4+"));
        assertTrue(ModCompatibilityChecker.isVersionCompatible("0.140.0+1.21.11", "0.138.4+"));
        assertFalse(ModCompatibilityChecker.isVersionCompatible("0.138.3+1.21.10", "0.138.4+"));
    }

    @Test void minimumAndRangeChecksKeepPrereleasePrecedence() {
        assertFalse(ModCompatibilityChecker.isVersionCompatible("0.138.4-beta.1+build", "0.138.4+"));
        assertTrue(ModCompatibilityChecker.isVersionCompatible("0.138.4+1.21.10", "0.138.0-0.140.0"));
        assertFalse(ModCompatibilityChecker.isVersionCompatible("0.141.0+1.21.10", "0.138.0-0.140.0"));
        assertTrue(ModCompatibilityChecker.isVersionCompatible("1.21.10", "1.21.10"));
        assertFalse(ModCompatibilityChecker.isVersionCompatible("unknown", "0.138.4+"));
    }
}
