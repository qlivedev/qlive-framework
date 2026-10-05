package io.github.qlivedev.graphql.beans;

import jakarta.persistence.Column;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;

/// A type with the numeric columns jOOQ writes precision and scale for, the ones it writes only a precision for, and
/// the ones it writes neither for.
public class DecimalResponse
{
    private BigDecimal amount;
    private BigDecimal count;
    private BigDecimal unbounded;
    private Timestamp created;
    private BigInteger mask;
    private BigInteger unboundedInt;


    @Column(name = "amount", precision = 30, scale = 10)
    public BigDecimal getAmount()
    {
        return amount;
    }


    public void setAmount(BigDecimal amount)
    {
        this.amount = amount;
    }


    @Column(name = "count", precision = 10)
    public BigDecimal getCount()
    {
        return count;
    }


    public void setCount(BigDecimal count)
    {
        this.count = count;
    }


    @Column(name = "unbounded")
    public BigDecimal getUnbounded()
    {
        return unbounded;
    }


    public void setUnbounded(BigDecimal unbounded)
    {
        this.unbounded = unbounded;
    }


    @Column(name = "created", precision = 6)
    public Timestamp getCreated()
    {
        return created;
    }


    public void setCreated(Timestamp created)
    {
        this.created = created;
    }


    @Column(name = "mask", precision = 39)
    public BigInteger getMask()
    {
        return mask;
    }


    public void setMask(BigInteger mask)
    {
        this.mask = mask;
    }


    @Column(name = "unbounded_int")
    public BigInteger getUnboundedInt()
    {
        return unboundedInt;
    }


    public void setUnboundedInt(BigInteger unboundedInt)
    {
        this.unboundedInt = unboundedInt;
    }
}
