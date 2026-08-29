package com.dataciders.qlive.model.condition;

import de.quinscape.spring.jsview.util.JSONUtil;
import org.svenson.ClassNameBasedTypeMapper;
import org.svenson.JSONParser;
import org.svenson.matcher.SubtypeMatcher;

/**
 * Specialized JSONParser setup to parse a FilterDSL node hierarchy into
 * the correct types.
 */
public final class ConditionParser
{
    private final JSONParser parser;

    public ConditionParser()
    {
        this.parser = new JSONParser();
        this.parser.setObjectSupport(JSONUtil.OBJECT_SUPPORT);

        ClassNameBasedTypeMapper typeMapper = new ClassNameBasedTypeMapper();
        typeMapper.setBasePackage(CNode.class.getPackage().getName());
        typeMapper.setEnforcedBaseType(CNode.class);
        typeMapper.setDiscriminatorField("type");
        typeMapper.setPathMatcher(new SubtypeMatcher(CNode.class));
        parser.setTypeMapper(typeMapper);

    }

    public CNode parse(String json)
    {
        return parser.parse(CNode.class, json);
    }

    public Condition parseCondition(String json)
    {
        final CNode node = parser.parse(CNode.class, json);

        if ((node != null && !(node instanceof Condition)))
        {
            throw new IllegalStateException("Parsed node is not a Condition: " + node);
        }
        
        return (Condition) node;
    }
}
