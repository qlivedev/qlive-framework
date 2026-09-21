package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.annotation.GraphQLField;
import io.github.qlivedev.graphql.docs.DocsExtractorTest;

/**
 * Target for {@link DocsExtractorTest}
 */
public class DocumentedBean
{
    private String name;
    private int num;


    /**
     * Name desc from getter
     *
     * @return
     */
    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public int getNum()
    {
        return num;
    }


    /**
     * Num desc from setter
     *
     * @return
     */
    public void setNum(int num)
    {
        this.num = num;
    }


    /**
     * Field with args
     *
     * @param name  name desc
     * @param num   num desc
     * @return
     */
    @GraphQLField
    public int getFieldWithArgs(
        String name,
        int num
    )
    {
        return name.hashCode() + num;
    }
}


