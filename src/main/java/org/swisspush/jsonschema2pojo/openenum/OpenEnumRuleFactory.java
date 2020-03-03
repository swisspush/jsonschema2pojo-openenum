package org.swisspush.jsonschema2pojo.openenum;

import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

public class OpenEnumRuleFactory extends RuleFactory {

    @Override
    public Rule<JClassContainer, JType> getEnumRule() {
        return new OpenEnumRule(this);
    }
}