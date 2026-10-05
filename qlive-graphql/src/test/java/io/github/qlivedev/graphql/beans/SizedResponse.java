package io.github.qlivedev.graphql.beans;

import jakarta.validation.constraints.Size;

/// A hand-written type with bounds jOOQ never generates: a minimum, and an annotation that bounds nothing.
public class SizedResponse
{
    private String code;
    private String note;
    private String plain;


    @Size(min = 2, max = 8)
    public String getCode()
    {
        return code;
    }


    public void setCode(String code)
    {
        this.code = code;
    }


    @Size
    public String getNote()
    {
        return note;
    }


    public void setNote(String note)
    {
        this.note = note;
    }


    public String getPlain()
    {
        return plain;
    }


    public void setPlain(String plain)
    {
        this.plain = plain;
    }
}
