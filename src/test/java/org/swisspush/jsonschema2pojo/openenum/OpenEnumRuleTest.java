package org.swisspush.jsonschema2pojo.openenum;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import org.jsonschema2pojo.Schema;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;

public class OpenEnumRuleTest {

    private Schema schema = mock(Schema.class);
    private OpenEnumRule rule = new OpenEnumRule(new OpenEnumRuleFactory());

    @Test
    public void testSimpleEnum() throws IOException {
        JCodeModel model = new JCodeModel();
        JPackage jpackage = model._package(getClass().getPackage().getName());

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add("open");
        arrayNode.add("closed");
        ObjectNode enumNode = objectMapper.createObjectNode();
        enumNode.put("type", "string");
        enumNode.set("enum", arrayNode);

        rule.apply("status", enumNode, null, jpackage, schema);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        model.build(new SingleStreamCodeWriter(os));
        
        String expected = "-----------------------------------org.swisspush.jsonschema2pojo.openenum.Status.java-----------------------------------\n" +
                "\n" +
                "package org.swisspush.jsonschema2pojo.openenum;\n" +
                "\n" +
                "import java.util.HashMap;\n" +
                "import java.util.Map;\n" +
                "import com.fasterxml.jackson.annotation.JsonCreator;\n" +
                "import com.fasterxml.jackson.annotation.JsonValue;\n" +
                "\n" +
                "public class Status {\n" +
                "\n" +
                "    private final static Map<String, Status> values = new HashMap<String, Status>();\n" +
                "    public final static Status OPEN = Status.fromString(\"OPEN\");\n" +
                "    public final static Status CLOSED = Status.fromString(\"CLOSED\");\n" +
                "    private String value;\n" +
                "\n" +
                "    private Status(String value) {\n" +
                "        this.value = value;\n" +
                "    }\n" +
                "\n" +
                "    @JsonCreator\n" +
                "    public static Status fromString(String s) {\n" +
                "        values.putIfAbsent(s, new Status(s));\n" +
                "        return values.get(s);\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    @JsonValue\n" +
                "    public String toString() {\n" +
                "        return this.value;\n" +
                "    }\n" +
                "\n" +
                "}" +
                "\n";

        assertEquals(expected, new String(os.toByteArray()).replace("\r\n", "\n"));
    }
}