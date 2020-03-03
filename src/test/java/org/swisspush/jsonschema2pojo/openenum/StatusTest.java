package org.swisspush.jsonschema2pojo.openenum;

import org.junit.Test;

import static org.junit.Assert.*;

public class StatusTest {
    @Test
    public void testOpenEnumPattern() {
        assertSame(Status.CLOSED, Status.fromString("CLOSED"));
        assertNotSame(Status.CLOSED, Status.OPEN);
        assertNotSame(Status.CLOSED, Status.fromString("OTHER"));
        assertEquals(Status.OPEN.toString(), "OPEN");
    }
}
