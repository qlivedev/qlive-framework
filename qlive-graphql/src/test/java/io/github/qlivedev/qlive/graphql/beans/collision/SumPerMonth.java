package io.github.qlivedev.qlive.graphql.beans.collision;

/**
 * Shares its simple name with {@link io.github.qlivedev.qlive.graphql.beans.SumPerMonth} and nothing else. Exists so a
 * test can build the one name clash the schema cannot resolve: two hand-written classes, neither of them a
 * generated POJO that the other could be overriding.
 */
public class SumPerMonth
{
    private int total;


    public int getTotal()
    {
        return total;
    }


    public void setTotal(int total)
    {
        this.total = total;
    }
}
