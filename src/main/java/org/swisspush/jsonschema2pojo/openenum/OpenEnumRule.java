package org.swisspush.jsonschema2pojo.openenum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.*;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.exception.ClassAlreadyExistsException;
import org.jsonschema2pojo.exception.GenerationException;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import java.util.*;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.WordUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.*;
import static org.jsonschema2pojo.rules.PrimitiveTypes.isPrimitive;
import static org.jsonschema2pojo.util.TypeUtil.resolveType;

public class OpenEnumRule implements Rule<JClassContainer, JType> {

    private static final String VALUE_FIELD_NAME = "value";

    private final RuleFactory ruleFactory;

    protected OpenEnumRule(RuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    @Override
    public JType apply(String nodeName, JsonNode node, JsonNode parent, JClassContainer container, Schema schema) {

        JDefinedClass _enum;
        try {
            _enum = createEnum(node, nodeName, container);
        } catch (ClassAlreadyExistsException e) {
            return e.getExistingClass();
        }

        schema.setJavaTypeIfEmpty(_enum);

        if (node.has("javaInterfaces")) {
            addInterfaces(_enum, node.get("javaInterfaces"));
        }

        // copy our node; remove the javaType as it will throw off the TypeRule for our case
        ObjectNode typeNode = node.deepCopy();
        typeNode.remove("javaType");

        // If type is specified on the enum, get a type rule for it.  Otherwise, we're a string.
        // (This is different from the default of Object, which is why we don't do this for every case.)
        JType backingType = node.has("type") ?
                ruleFactory.getTypeRule().apply(nodeName, typeNode, parent, container, schema) :
                container.owner().ref(String.class);

        JMethod factoryMethod = addFactoryMethod(_enum, backingType);
        addEnumConstants(node.path("enum"), _enum, node.path("javaEnumNames"), backingType, factoryMethod);
        JFieldVar valueField = addValueField(_enum, backingType);
        addToString(_enum, valueField);

        return _enum;
    }

    private JDefinedClass createEnum(JsonNode node, String nodeName, JClassContainer container) throws ClassAlreadyExistsException {

        try {
            if (node.has("javaType")) {
                String fqn = node.get("javaType").asText();

                if (isPrimitive(fqn, container.owner())) {
                    throw new GenerationException("Primitive type '" + fqn + "' cannot be used as an enum.");
                }

                try {
                    Class<?> existingClass = Thread.currentThread().getContextClassLoader().loadClass(fqn);
                    throw new ClassAlreadyExistsException(container.owner().ref(existingClass));
                } catch (ClassNotFoundException e) {
                    return container.owner()._class(fqn, ClassType.ENUM);
                }
            } else {
                try {
                    return container._class(JMod.PUBLIC, getEnumName(nodeName, node, container), ClassType.CLASS);
                } catch (JClassAlreadyExistsException e) {
                    throw new GenerationException(e);
                }
            }
        } catch (JClassAlreadyExistsException e) {
            throw new ClassAlreadyExistsException(e.getExistingClass());
        }
    }

    private JMethod addFactoryMethod(JDefinedClass _enum, JType backingType) {
        JFieldVar quickLookupMap = addQuickLookupMap(_enum, backingType);

        JMethod fromString = _enum.method(JMod.PUBLIC | JMod.STATIC, _enum, "fromString");
        JVar valueParam = fromString.param(backingType, "s");

        JBlock body = fromString.body();
        body.add(quickLookupMap.invoke("putIfAbsent")
                .arg(valueParam)
                .arg(JExpr._new(_enum).arg(valueParam)));
        body._return(quickLookupMap
                .invoke("get")
                .arg(valueParam));

        ruleFactory.getAnnotator().enumCreatorMethod(_enum, fromString);

        return fromString;
    }

    private JFieldVar addQuickLookupMap(JDefinedClass _enum, JType backingType) {

        JClass lookupType = _enum.owner().ref(Map.class).narrow(backingType.boxify(), _enum);
        JFieldVar lookupMap = _enum.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, lookupType, "values");

        JClass lookupImplType = _enum.owner().ref(HashMap.class).narrow(backingType.boxify(), _enum);
        lookupMap.init(JExpr._new(lookupImplType));

        return lookupMap;
    }

    private JFieldVar addValueField(JDefinedClass _enum, JType type) {
        JFieldVar valueField = _enum.field(JMod.PRIVATE, type, VALUE_FIELD_NAME);

        JMethod constructor = _enum.constructor(JMod.PRIVATE);
        JVar valueParam = constructor.param(type, VALUE_FIELD_NAME);
        JBlock body = constructor.body();
        body.assign(JExpr._this().ref(valueField), valueParam);

        return valueField;
    }

    private void addToString(JDefinedClass _enum, JFieldVar valueField) {
        JMethod toString = _enum.method(JMod.PUBLIC, String.class, "toString");
        JBlock body = toString.body();

        JExpression toReturn = JExpr._this().ref(valueField);
        if(!isString(valueField.type())){
            toReturn = toReturn.plus(JExpr.lit(""));
        }

        body._return(toReturn);

        toString.annotate(Override.class);
        ruleFactory.getAnnotator().enumValueMethod(_enum, toString);
    }

    private boolean isString(JType type){
        return type.fullName().equals(String.class.getName());
    }

    private void addEnumConstants(JsonNode node, JDefinedClass _enum, JsonNode customNames, JType type, JMethod factoryMethod) {
        Collection<String> existingConstantNames = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode value = node.path(i);

            if (!value.isNull()) {
                String constantName = getConstantName(value.asText(), customNames.path(i).asText());
                constantName = makeUnique(constantName, existingConstantNames);
                existingConstantNames.add(constantName);

                JFieldVar constant = _enum.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, _enum, constantName);

                constant.init(_enum.staticInvoke(factoryMethod).arg(JExpr.lit(constantName)));
            }
        }
    }

    private String getEnumName(String nodeName, JsonNode node, JClassContainer container) {
        String fieldName = ruleFactory.getNameHelper().getClassName(nodeName, node);
        String className = ruleFactory.getNameHelper().replaceIllegalCharacters(capitalize(fieldName));
        String normalizedName = ruleFactory.getNameHelper().normalizeName(className);

        Collection<String> existingClassNames = new ArrayList<>();
        for (Iterator<JDefinedClass> classes = container.classes(); classes.hasNext();) {
            existingClassNames.add(classes.next().name());
        }
        return makeUnique(normalizedName, existingClassNames);
    }

    private String makeUnique(String name, Collection<String> existingNames) {
        boolean found = false;
        for (String existingName : existingNames) {
            if (name.equalsIgnoreCase(existingName)) {
                found = true;
                break;
            }
        }
        if (found) {
            name = makeUnique(name + "_", existingNames);
        }
        return name;
    }

    protected String getConstantName(String nodeName, String customName) {
        if (isNotBlank(customName)) {
            return customName;
        }

        List<String> enumNameGroups = new ArrayList<>(asList(splitByCharacterTypeCamelCase(nodeName)));

        String enumName = "";
        for (Iterator<String> iter = enumNameGroups.iterator(); iter.hasNext();) {
            if (containsOnly(ruleFactory.getNameHelper().replaceIllegalCharacters(iter.next()), "_")) {
                iter.remove();
            }
        }

        enumName = upperCase(join(enumNameGroups, "_"));

        if (isEmpty(enumName)) {
            enumName = "__EMPTY__";
        } else if (Character.isDigit(enumName.charAt(0))) {
            enumName = "_" + enumName;
        }

        return enumName;
    }

    private void addInterfaces(JDefinedClass jclass, JsonNode javaInterfaces) {
        for (JsonNode i : javaInterfaces) {
            jclass._implements(resolveType(jclass._package(), i.asText()));
        }
    }

}