# jsonschema2pojo-openenum
Generate enums as relaxed type-safe enum classes supporting unknown values.

This allows backward compatibility when JSON schemas get new enum values 
whereas existing code does not yet know them.

The generated code looks like:

```java
package org.swisspush.jsonschema2pojo.openenum;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Status {

    private final static Map<String, Status> values = new HashMap<String, Status>();
    public final static Status OPEN = Status.fromString("OPEN");
    public final static Status CLOSED = Status.fromString("CLOSED");
    private String value;

    private Status(String value) {
        this.value = value;
    }

    @JsonCreator
    public static Status fromString(String s) {
        values.putIfAbsent(s, new Status(s));
        return values.get(s);
    }

    @Override
    @JsonValue
    public String toString() {
        return this.value;
    }

}
```

