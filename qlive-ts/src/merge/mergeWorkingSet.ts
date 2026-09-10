import {GraphQLQuery} from "../GraphQLQuery";
import {EntityChange, EntityDeletion, MergeConfig, MergeResult} from "./types";

/**
 * QLive's write mutation, and the only one an application needs in order to store anything.
 *
 * The selection is fixed because the result type is: a merge answers with a status and, where it did not
 * land, one conflict per row that stood in the way. There is nothing here an application would want to
 * select differently, and the working set reads all of it.
 *
 * Declared at module scope like any other query. Parsing needs the source and nothing else, so this costs
 * an import; the conversion map it needs the schema for is built on first use, long after startup().
 */
const MERGE_WORKING_SET = new GraphQLQuery<MergeResult>(
    `mutation mergeWorkingSet($changes: [EntityChangeInput]!, $deletions: [EntityDeletionInput]!, $mergeConfig: MergeConfigInput!)
    {
        mergeWorkingSet(changes: $changes, deletions: $deletions, mergeConfig: $mergeConfig)
        {
            status
            conflicts
            {
                type
                id
                storedVersion
                deleted
                fields
                {
                    field
                    mine
                    stored
                    informational
                }
            }
        }
    }`
)

/**
 * Writes one working set: every change and every deletion in one transaction, or none of them.
 *
 * The plain call, without a working set around it -- what a service with nobody in front of it needs, and
 * what WorkingSet#merge() runs underneath.
 *
 * @param changes       rows to insert or update
 * @param deletions     rows to remove
 * @param mergeConfig   what the caller can do about a conflict
 *
 * @returns whether it landed, and what stood in the way if it did not
 */
export function mergeWorkingSet(
    changes: EntityChange[],
    deletions: EntityDeletion[],
    mergeConfig: MergeConfig
): Promise<MergeResult>
{
    return MERGE_WORKING_SET.execute({changes, deletions, mergeConfig})
}
