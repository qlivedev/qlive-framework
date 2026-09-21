package io.github.qlivedev.model.test;

import de.quinscape.domainql.annotation.GraphQLField;
import de.quinscape.domainql.generic.DomainObject;
import de.quinscape.domainql.generic.GenericScalar;
import de.quinscape.domainql.jsonb.JSONB;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.runtime.scalar.ComputedValue;
import io.github.qlivedev.runtime.scalar.FilterDSL;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Test type containing all standard scalar types
 */
public class AllScalars
{
    private boolean bool;
    private int intValue;
    private double doubleValue;
    private String stringValue;
    private Timestamp timestampValue;
    private Date dateValue;
    private long longValue;
    private long currencyValue;
    private byte byteValue;
    private DomainObject domainObjectValue;
    private JSONB jsonbValue;
    private CNode conditionValue;
    private CNode fieldExpressionValue;
    private GenericScalar genericScalarValue;
    private BigDecimal bigDecimalValue;
    private QueryConfig queryConfigValue;
    private ComputedValue computedValue;


    public static AllScalars create(DomainObject domainObject)
    {
        final AllScalars allScalars = new AllScalars();

        allScalars.setBool(true);
        allScalars.setIntValue(12);
        allScalars.setDoubleValue(12.34);
        allScalars.setStringValue("abc");
        allScalars.setTimestampValue(Timestamp.valueOf(LocalDateTime.now()));
        allScalars.setDateValue(Date.valueOf(LocalDate.now()));
        allScalars.setLongValue(12345678901L);
        allScalars.setCurrencyValue(100000);
        allScalars.setByteValue((byte) 23);
        allScalars.setDomainObjectValue(domainObject);
        allScalars.setJsonbValue(new JSONB(new HashMap<>()));
        allScalars.setConditionValue(
            FilterDSL.field("test").eq(
                FilterDSL.value("abc")
            )
        );
        allScalars.setFieldExpressionValue(
            FilterDSL.field("test")
        );
        allScalars.setGenericScalarValue(new GenericScalar("String", "psych"));
        allScalars.setBigDecimalValue(new BigDecimal("12345678901234567890"));
        final QueryConfig queryConfig = new QueryConfig();
        queryConfig.setPageSize(20);
        queryConfig.setSortFields(List.of(FilterDSL.fieldExpression("id")));
        allScalars.setQueryConfigValue(queryConfig);
        allScalars.setComputedValueScalar(new ComputedValue("foo", List.of(new GenericScalar("String", "bar"))));

        return allScalars;
    }


    public boolean isBool()
    {
        return bool;
    }


    public void setBool(boolean bool)
    {
        this.bool = bool;
    }


    public int getIntValue()
    {
        return intValue;
    }


    public void setIntValue(int intValue)
    {
        this.intValue = intValue;
    }


    public double getDoubleValue()
    {
        return doubleValue;
    }


    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }


    public String getStringValue()
    {
        return stringValue;
    }


    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }


    public Timestamp getTimestampValue()
    {
        return timestampValue;
    }


    public void setTimestampValue(Timestamp timestampValue)
    {
        this.timestampValue = timestampValue;
    }


    public Date getDateValue()
    {
        return dateValue;
    }


    public void setDateValue(Date dateValue)
    {
        this.dateValue = dateValue;
    }

    @GraphQLField(type = "Long")
    public long getLongValue()
    {
        return longValue;
    }


    public void setLongValue(long longValue)
    {
        this.longValue = longValue;
    }


    @GraphQLField(type = "Currency")
    public long getCurrencyValue()
    {
        return currencyValue;
    }


    public void setCurrencyValue(long currencyValue)
    {
        this.currencyValue = currencyValue;
    }


    public byte getByteValue()
    {
        return byteValue;
    }


    public void setByteValue(byte byteValue)
    {
        this.byteValue = byteValue;
    }


    public DomainObject getDomainObjectValue()
    {
        return domainObjectValue;
    }


    public void setDomainObjectValue(DomainObject domainObjectValue)
    {
        this.domainObjectValue = domainObjectValue;
    }


    public JSONB getJsonbValue()
    {
        return jsonbValue;
    }


    public void setJsonbValue(JSONB jsonbValue)
    {
        this.jsonbValue = jsonbValue;
    }


    public CNode getConditionValue()
    {
        return conditionValue;
    }


    public void setConditionValue(CNode conditionValue)
    {
        this.conditionValue = conditionValue;
    }


    public CNode getFieldExpressionValue()
    {
        return fieldExpressionValue;
    }


    public void setFieldExpressionValue(CNode fieldExpressionValue)
    {
        this.fieldExpressionValue = fieldExpressionValue;
    }


    public GenericScalar getGenericScalarValue()
    {
        return genericScalarValue;
    }


    public void setGenericScalarValue(GenericScalar genericScalarValue)
    {
        this.genericScalarValue = genericScalarValue;
    }


    public BigDecimal getBigDecimalValue()
    {
        return bigDecimalValue;
    }


    public void setBigDecimalValue(BigDecimal bigDecimalValue)
    {
        this.bigDecimalValue = bigDecimalValue;
    }


    public QueryConfig getQueryConfigValue()
    {
        return queryConfigValue;
    }


    public void setQueryConfigValue(QueryConfig queryConfigValue)
    {
        this.queryConfigValue = queryConfigValue;
    }


    public ComputedValue getComputedValueScalar()
    {
        return computedValue;
    }


    public void setComputedValueScalar(ComputedValue computedValue)
    {
        this.computedValue = computedValue;
    }
}
