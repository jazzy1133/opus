package com.opus.music.party

/**
 * Pure guest-policy logic for Party Queue (no Android dependencies,
 * unit-testable on the JVM).
 *
 * Each guest is identified by IP address. Guests may add a limited number
 * of songs and vote once per song.
 *
 * Thread safety: the embedded web server calls into this from its own
 * request threads, so every method is synchronized.
 */
class PartyPolicy(
    @Volatile var maxAddsPerGuest: Int = 3,
    @Volatile var votingEnabled: Boolean = true
) {
    private val addsUsed = mutableMapOf<String, Int>()
    private val votes = mutableMapOf<String, MutableSet<String>>() // songId -> guest ips

    /** True if this guest may add another song right now. */
    @Synchronized
    fun canAdd(guestIp: String): Boolean =
        (addsUsed[guestIp] ?: 0) < maxAddsPerGuest.coerceAtLeast(0)

    @Synchronized
    fun recordAdd(guestIp: String) {
        addsUsed[guestIp] = (addsUsed[guestIp] ?: 0) + 1
    }

    /**
     * Atomically check the limit and consume one add.
     * @return true if the add was allowed (and counted).
     */
    @Synchronized
    fun tryAdd(guestIp: String): Boolean {
        if (!canAdd(guestIp)) return false
        recordAdd(guestIp)
        return true
    }

    @Synchronized
    fun addsRemaining(guestIp: String): Int =
        (maxAddsPerGuest - (addsUsed[guestIp] ?: 0)).coerceAtLeast(0)

    /**
     * Cast (or retract) a vote. Returns true if the song is now voted by
     * this guest, false if the vote was retracted or voting is disabled.
     */
    @Synchronized
    fun toggleVote(songId: String, guestIp: String): Boolean {
        if (!votingEnabled) return false
        val set = votes.getOrPut(songId) { mutableSetOf() }
        return if (set.contains(guestIp)) {
            set.remove(guestIp)
            false
        } else {
            set.add(guestIp)
            true
        }
    }

    @Synchronized
    fun voteCount(songId: String): Int = votes[songId]?.size ?: 0

    @Synchronized
    fun hasVoted(songId: String, guestIp: String): Boolean =
        votes[songId]?.contains(guestIp) == true

    /** Order song ids by votes (desc), stable for ties. */
    @Synchronized
    fun orderByVotes(ids: List<String>): List<String> =
        ids.sortedWith(compareByDescending<String> { voteCount(it) }.thenBy { ids.indexOf(it) })

    @Synchronized
    fun clearVotesFor(songId: String) {
        votes.remove(songId)
    }

    @Synchronized
    fun reset() {
        addsUsed.clear()
        votes.clear()
    }
}
