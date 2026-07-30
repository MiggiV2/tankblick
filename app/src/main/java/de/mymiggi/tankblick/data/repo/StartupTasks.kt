package de.mymiggi.tankblick.data.repo

/**
 * Housekeeping that runs once per process start.
 *
 * Exists as its own type so the work is testable: calling it straight from
 * `Application.onCreate` would leave the wiring itself unverified, and
 * retention that is only documented but never invoked is the same as no
 * retention at all.
 */
class StartupTasks(
    private val stationRepository: StationRepository,
) {

    /**
     * Drops price history the app has no use for.
     *
     * @return how many snapshots were removed.
     */
    suspend fun run(): Int = stationRepository.purgeOldSnapshots()
}
