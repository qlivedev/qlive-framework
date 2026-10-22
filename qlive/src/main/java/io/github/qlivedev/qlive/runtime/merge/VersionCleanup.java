package io.github.qlivedev.qlive.runtime.merge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/// Drops the version records that have outlived their use, and the field sets nothing names any more.
///
/// The lifetime is what a base version is worth. Past it the chain walk cannot say what changed since, so a
/// concurrent change becomes a conflict on every field rather than the silent merge it would have been --
/// which is a cost to whoever parked their work, never a wrong answer. Nothing else expires: the rows
/// themselves stay, and so does a row's version column, which is why nothing has a foreign key onto
/// `app_version`.
///
/// Seven days rather than the two Automaton kept. Friday evening to Monday morning is 72 hours and a change
/// set parked across exactly that gap is the case this feature exists for; storing the field sets is what
/// makes the longer window safe, a deployment in between costing accuracy rather than a mask.
public class VersionCleanup
{
    private final static Logger log = LoggerFactory.getLogger(VersionCleanup.class);

    private final VersionService versionService;

    private final VersionHolder versionHolder;

    private final FieldSetService fieldSetService;

    private final Duration versionLifetime;


    public VersionCleanup(
        VersionService versionService,
        VersionHolder versionHolder,
        FieldSetService fieldSetService,
        Duration versionLifetime
    )
    {
        this.versionService = versionService;
        this.versionHolder = versionHolder;
        this.fieldSetService = fieldSetService;
        this.versionLifetime = versionLifetime;
    }


    /// Hourly, and once at startup after a minute's grace. Hourly rather than daily because the run is one
    /// indexed delete and because an application that is restarted every afternoon would otherwise never
    /// get round to it.
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public void sweep()
    {
        expire();
    }


    /// One sweep, and how many records went. Separate from the schedule so that a test can run it without
    /// waiting for one, and so that an application with a reason to sweep at a particular moment can.
    public int expire()
    {
        final Timestamp before = Timestamp.from(Instant.now().minus(versionLifetime));

        final int dropped = versionService.prune(before);
        final int forgotten = versionHolder.dropOlderThan(before);

        // after the records, so that a field set is dropped only once nothing names it, and held to the same
        // cutoff -- a field set stored since then may be a node coming up that has not written its first
        // record yet
        final int fieldSets = fieldSetService.pruneUnused(before);

        if (dropped != 0 || forgotten != 0 || fieldSets != 0)
        {
            log.info(
                "Expired {} version records ({} of them held), and {} field sets",
                dropped, forgotten, fieldSets
            );
        }

        return dropped;
    }
}
