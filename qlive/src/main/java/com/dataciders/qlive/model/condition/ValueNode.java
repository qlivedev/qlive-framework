package com.dataciders.qlive.model.condition;

import org.svenson.JSONProperty;

/// Abstract base class for "Value nodes" within a FilterDSL condition hierarchy
public abstract class ValueNode
    extends CNode
{


    public Operation bitNand(CNode a) { return Operation.create("bitNand", this, a); }
    public Operation mod(CNode a) { return Operation.create("mod", this, a); }
    public Operation div(CNode a) { return Operation.create("div", this, a); }
    public Operation neg() { return Operation.create("neg", this); }
    public Operation rem(CNode a) { return Operation.create("rem", this, a); }
    public Operation add(CNode a) { return Operation.create("add", this, a); }
    public Operation subtract(CNode a) { return Operation.create("subtract", this, a); }
    public Operation plus(CNode a) { return Operation.create("plus", this, a); }
    public Operation bitAnd(CNode a) { return Operation.create("bitAnd", this, a); }
    public Operation bitXor(CNode a) { return Operation.create("bitXor", this, a); }
    public Operation shl(CNode a) { return Operation.create("shl", this, a); }
    public Operation unaryMinus() { return Operation.create("unaryMinus", this); }
    public Operation bitNor(CNode a) { return Operation.create("bitNor", this, a); }
    public Operation shr(CNode a) { return Operation.create("shr", this, a); }
    public Operation modulo(CNode a) { return Operation.create("modulo", this, a); }
    public Operation bitXNor(CNode a) { return Operation.create("bitXNor", this, a); }
    public Operation bitNot() { return Operation.create("bitNot", this); }
    public Operation sub(CNode a) { return Operation.create("sub", this, a); }
    public Operation minus(CNode a) { return Operation.create("minus", this, a); }
    public Operation mul(CNode a) { return Operation.create("mul", this, a); }
    public Operation bitOr(CNode a) { return Operation.create("bitOr", this, a); }
    public Operation times(CNode a) { return Operation.create("times", this, a); }
    public Operation pow(CNode a) { return Operation.create("pow", this, a); }
    public Operation divide(CNode a) { return Operation.create("divide", this, a); }
    public Operation power(CNode a) { return Operation.create("power", this, a); }
    public Operation multiply(CNode a) { return Operation.create("multiply", this, a); }
    public Operation unaryPlus() { return Operation.create("unaryPlus", this); }
    public Operation lower() { return Operation.create("lower", this); }
    public Operation upper() { return Operation.create("upper", this); }
    public Operation concat(CNode a) { return Operation.create("concat", this, a); }
    public Operation asc() { return Operation.create("asc", this); }
    public Operation desc() { return Operation.create("desc", this); }

    // CONDITIONS /////////////////////
    //
    // The four no-argument ones below are marked as non-properties. Svenson reads any no-argument
    // "isXxx()" method as a getter, whatever it returns, so isNull() would be a property named "null"
    // whose value is a Condition wrapping the very node being serialized -- a structure that recurses
    // forever. They are DSL builders, not state, and nothing about a node should be read through them.
    public Condition greaterOrEqual(CNode a) { return Condition.create("greaterOrEqual", this, a); }
    public Condition lessOrEqual(CNode a) { return Condition.create("lessOrEqual", this, a); }
    public Condition lt(CNode a) { return Condition.create("lt", this, a); }
    public Condition notBetweenSymmetric(CNode a, CNode b) { return Condition.create("notBetweenSymmetric", this, a, b); }
    public Condition notEqualIgnoreCase(CNode a) { return Condition.create("notEqualIgnoreCase", this, a); }
    public Condition betweenSymmetric(CNode a, CNode b) { return Condition.create("betweenSymmetric", this, a, b); }
    public Condition lessThan(CNode a) { return Condition.create("lessThan", this, a); }
    public Condition equalIgnoreCase(CNode a) { return Condition.create("equalIgnoreCase", this, a); }
    public Condition isDistinctFrom(CNode a) { return Condition.create("isDistinctFrom", this, a); }
    public Condition between(CNode a, CNode b) { return Condition.create("between", this, a, b); }
    public Condition ge(CNode a) { return Condition.create("ge", this, a); }
    public Condition greaterThan(CNode a) { return Condition.create("greaterThan", this, a); }
    @JSONProperty(ignore = true) public Condition isNotNull() { return Condition.create("isNotNull", this); }
    public Condition notLikeRegex(CNode a) { return Condition.create("notLikeRegex", this, a); }
    public Condition notBetween(CNode a, CNode b) { return Condition.create("notLikeRegex", this, a, b); }
    public Condition notEqual(CNode a) { return Condition.create("notEqual", this, a); }
    @JSONProperty(ignore = true) public Condition isFalse() { return Condition.create("isFalse", this); }
    public Condition containsIgnoreCase(CNode a) { return Condition.create("containsIgnoreCase", this, a); }
    public Condition eq(CNode a) { return Condition.create("eq", this, a); }
    public Condition gt(CNode a) { return Condition.create("gt", this, a); }
    public Condition equal(CNode a) { return Condition.create("equal", this, a); }
    public Condition likeRegex(CNode a) { return Condition.create("likeRegex", this, a); }
    @JSONProperty(ignore = true) public Condition isTrue() { return Condition.create("isTrue", this); }
    public Condition contains(CNode a) { return Condition.create("contains", this, a); }
    public Condition notContainsIgnoreCase(CNode a) { return Condition.create("notContainsIgnoreCase", this, a); }
    public Condition notContains(CNode a) { return Condition.create("notContains", this, a); }
    public Condition ne(CNode a) { return Condition.create("ne", this, a); }
    @JSONProperty(ignore = true) public Condition isNull() { return Condition.create("isNull", this); }
    public Condition endsWith(CNode a) { return Condition.create("endsWith", this, a); }
    public Condition le(CNode a) { return Condition.create("le", this, a); }
    public Condition isNotDistinctFrom(CNode a) { return Condition.create("isNotDistinctFrom", this, a); }
    public Condition startsWith(CNode a) { return Condition.create("startsWith", this, a); }
    public Condition in(Values values) { return Condition.create("in", this, values); }

}
