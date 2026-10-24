package io.github.qlivedev.qlive.runtime.merge;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// What the merges of the running transaction wrote, for a caller that has to tell somebody about it: an action
/// tells the client that issued it, whose working sets hold the rows.
///
/// Collected only where a caller asked with {@link #collect()}, which binds the collection to the running
/// transaction. Every merge done in it adds its writes once it is done -- through the {@link EntityWriter} and
/// through {@link MergeService#mergeInTransaction} alike -- and a merge that conflicted adds nothing, having
/// written nothing that stays.
public final class WrittenRows
{
    private final List<WrittenRow> rows = new ArrayList<>();


    private WrittenRows()
    {
    }


    /// Starts collecting the writes of the running transaction, or returns the collection a caller further out
    /// started. It ends with the transaction.
    ///
    /// @return the collection
    ///
    /// @throws IllegalStateException where no transaction is running
    public static WrittenRows collect()
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            throw new IllegalStateException("Writes are collected in a transaction, and none is running.");
        }

        if (TransactionSynchronizationManager.getResource(WrittenRows.class) instanceof WrittenRows running)
        {
            return running;
        }

        final WrittenRows rows = new WrittenRows();
        TransactionSynchronizationManager.bindResource(WrittenRows.class, rows);
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization()
            {
                @Override
                public void afterCompletion(int status)
                {
                    TransactionSynchronizationManager.unbindResourceIfPossible(WrittenRows.class);
                }
            }
        );
        return rows;
    }


    /// Adds what one merge wrote, where the running transaction collects.
    static void add(List<WrittenRow> written)
    {
        if (TransactionSynchronizationManager.getResource(WrittenRows.class) instanceof WrittenRows rows)
        {
            rows.rows.addAll(written);
        }
    }


    /// The writes so far, in the order they were made.
    public List<WrittenRow> getRows()
    {
        return Collections.unmodifiableList(rows);
    }
}
