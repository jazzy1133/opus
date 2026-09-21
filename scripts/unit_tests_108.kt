import com.opus.music.party.PartyPolicy
import com.opus.music.data.MixCandidate
import com.opus.music.data.SongMeta
import com.opus.music.data.OfflineMixSelector

var failures = 0
fun check(name: String, cond: Boolean) {
    if (cond) println("  ok: $name") else { println("  FAIL: $name"); failures++ }
}

fun main() {
    println("== PartyPolicy ==")
    val p = PartyPolicy(maxAddsPerGuest = 3, votingEnabled = true)
    check("guest can add initially", p.canAdd("1.2.3.4"))
    check("3 remaining at start", p.addsRemaining("1.2.3.4") == 3)
    p.recordAdd("1.2.3.4"); p.recordAdd("1.2.3.4"); p.recordAdd("1.2.3.4")
    check("limit enforced after 3 adds", !p.canAdd("1.2.3.4"))
    check("0 remaining after limit", p.addsRemaining("1.2.3.4") == 0)
    check("other guest unaffected", p.canAdd("5.6.7.8"))
    check("vote toggles on", p.toggleVote("s1", "1.2.3.4"))
    check("vote count 1", p.voteCount("s1") == 1)
    check("hasVoted true", p.hasVoted("s1", "1.2.3.4"))
    check("second guest vote", p.toggleVote("s1", "5.6.7.8"))
    check("vote count 2", p.voteCount("s1") == 2)
    check("vote toggles off (retract)", !p.toggleVote("s1", "1.2.3.4"))
    check("vote count back to 1", p.voteCount("s1") == 1)
    p.toggleVote("s2", "9.9.9.9"); p.toggleVote("s2", "8.8.8.8"); p.toggleVote("s2", "7.7.7.7")
    val ordered = p.orderByVotes(listOf("s1", "s2", "s3"))
    check("most-voted first", ordered == listOf("s2", "s1", "s3"))
    p.votingEnabled = false
    check("voting disabled blocks votes", !p.toggleVote("s9", "1.2.3.4"))
    check("vote count 0 when disabled", p.voteCount("s9") == 0)
    val p0 = PartyPolicy(maxAddsPerGuest = 0)
    check("maxAdds=0 blocks all", !p0.canAdd("1.1.1.1"))
    p.reset()
    check("reset clears limits", p.canAdd("1.2.3.4"))
    check("reset clears votes", p.voteCount("s1") == 0)

    println("== OfflineMixSelector ==")
    fun cand(id: String, plays: Int) = MixCandidate(
        SongMeta(id, "Title $id"), playCount = plays, starred = false)
    val cands = listOf(cand("a", 10), cand("b", 5), cand("c", 20), cand("d", 15))
    // starred first even with fewer plays
    val m1 = OfflineMixSelector.selectMix(cands, setOf("b"), emptySet(), 10)
    check("starred first", m1.first().id == "b")
    check("then by play count", m1.map { it.id } == listOf("b", "c", "d", "a"))
    // downloaded excluded
    val m2 = OfflineMixSelector.selectMix(cands, emptySet(), setOf("c", "a"), 10)
    check("downloaded skipped", m2.map { it.id } == listOf("d", "b"))
    // size cap
    val m3 = OfflineMixSelector.selectMix(cands, emptySet(), emptySet(), 2)
    check("size cap respected", m3.map { it.id } == listOf("c", "d"))
    // zero size
    check("zero size -> empty", OfflineMixSelector.selectMix(cands, emptySet(), emptySet(), 0).isEmpty())
    // dupes collapsed
    val m5 = OfflineMixSelector.selectMix(
        listOf(cand("a", 10), cand("a", 10)), setOf("a"), emptySet(), 10)
    check("no duplicates", m5.size == 1)

    println(if (failures == 0) "ALL UNIT TESTS PASSED" else "$failures FAILURES")
    kotlin.system.exitProcess(if (failures == 0) 0 else 1)
}
