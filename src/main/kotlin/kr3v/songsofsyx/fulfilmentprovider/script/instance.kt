package kr3v.songsofsyx.fulfilmentprovider.script

import game.time.TIME
import kr3v.songsofsyx.fulfilmentprovider.Either
import kr3v.songsofsyx.fulfilmentprovider.ErrorS
import kr3v.songsofsyx.fulfilmentprovider.Log
import script.SCRIPT.SCRIPT_INSTANCE
import settlement.entity.humanoid.Humanoid
import settlement.entity.humanoid.ai.main.AIManager
import settlement.main.SETT
import settlement.stats.STATS
import snake2d.util.datatypes.COORDINATE
import snake2d.util.file.FileGetter
import snake2d.util.file.FilePutter
import snake2d.util.sprite.text.Str
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random


/*
TODO:
- add shrines, temples
- check other services
- render service providers (rooms) on the map
- fix startup when script is not loaded for a map
- log path / log library
- render not plebs, but their homes
 */

object Services {
    enum class Type {
        HEARTH, BATH, WELL, BARBER, BROTHEL, LAVATORY, PHYSICIAN, SPEAKER, STAGE, ARENA, GARENA, CANTEEN, EATERY, TAVERN, MARKET
    }

    val services by lazy {
        mapOf(
            Type.HEARTH to (STATS.SERVICE().get(SETT.ROOMS().HEARTH.service())),
            Type.BATH to (STATS.SERVICE().get(SETT.ROOMS().BATHS.get(0).service())),
            Type.WELL to (STATS.SERVICE().get(SETT.ROOMS().WELLS.get(0).service())),
            Type.BARBER to (STATS.SERVICE().get(SETT.ROOMS().BARBERS.get(0).service())),
            Type.BROTHEL to (STATS.SERVICE().get(SETT.ROOMS().BROTHELS.get(0).service())),
            Type.LAVATORY to (STATS.SERVICE().get(SETT.ROOMS().LAVATORIES.get(0).service())),
            Type.PHYSICIAN to (STATS.SERVICE().get(SETT.ROOMS().PHYSICIANS.get(0).service())),
            Type.SPEAKER to (STATS.SERVICE().get(SETT.ROOMS().SPEAKERS.get(0).service())),
            Type.STAGE to (STATS.SERVICE().get(SETT.ROOMS().STAGES.get(0).service())),
            Type.ARENA to (STATS.SERVICE().get(SETT.ROOMS().ARENAS.get(0).service())),
            Type.GARENA to (STATS.SERVICE().get(SETT.ROOMS().GARENAS.get(0).service())),
            Type.CANTEEN to (STATS.SERVICE().get(SETT.ROOMS().CANTEENS.get(0).service())),
            Type.EATERY to (STATS.SERVICE().get(SETT.ROOMS().EATERIES.get(0).service())),
            Type.TAVERN to (STATS.SERVICE().get(SETT.ROOMS().TAVERNS.get(0).service())),
            Type.MARKET to (STATS.SERVICE().get(SETT.ROOMS().MARKET.get(0).service()))
        )
    }
}

///

class HumanoidInfoProvider {
    fun pos(h: Humanoid): COORDINATE = h.tc()

    data class ServiceAccess(
        val total: Double,
        val access: Double,
        val proximity: Double,
        val quality: Double,
    )

    fun servicesAccess(h: Humanoid): Map<Services.Type, ServiceAccess> =
        Services.services.mapValues { (_, service) ->
            ServiceAccess(
                service.total(h), if (service.access(h)) 1.0 else 0.0, service.proximity(h), service.quality(h)
            )
        }

    data class AI(
        val destination: COORDINATE?,
        val resourceCarried: String,
        val resourceA: Int,
        val occupation: String,
        val state: String,
        val plan: String,
    )

    fun ai(h: Humanoid): AI {
        val ai = h.ai() as? AIManager
        return AI(
            destination = h.ai().destination,
            resourceCarried = h.ai().resourceCarried()?.name.toString(),
            resourceA = h.ai().resourceA(),
            occupation = Str("").also { h.ai().getOccupation(h, it) }.toString(),
            state = ai?.state()?.key ?: "null",
            plan = ai?.plan()?.key ?: "null"
        )
    }
}

typealias State = List<Humanoid>

interface HumanoidListProvider {
    fun getHumanoids(): Either<List<Humanoid>, ErrorS>
}

class FulfilmentProviderScriptInstance : SCRIPT_INSTANCE, HumanoidListProvider {

    lateinit var occInfoCollector: OccupationInfoCollector

    private var lastDays = -1

    override fun update(d: Double) {
        if (d <= 1e-6) {
            return
        }

        val currDay = TIME.days().bitsSinceStart()
        if (currDay == lastDays) {
            return
        }
        lastDays = currDay

        Log.println("FulfilmentProviderScriptInstance update: d=$d, currDay=$currDay, lastDays=$lastDays")

        val ents = SETT.ENTITIES()
        val stateUpd = ents.allEnts.mapNotNull { e -> e as? Humanoid }
            .filter { e -> e.indu().hType().player && e.indu().popCL().cl.key != "CHILD" }

        stateLock.lock()
        state = stateUpd
        stateLock.unlock()

        occInfoCollector.update(d)
    }

    override fun save(p0: FilePutter?) {
    }

    override fun load(p0: FileGetter?) {
    }

    ///

    private val stateLock = ReentrantLock()
    private lateinit var state: List<Humanoid>

    override fun getHumanoids(): Either<List<Humanoid>, ErrorS> =
        if (!::state.isInitialized) Either.Right(ErrorS("State not initialized"))
        else Either.Left(state)
}

class OccupationInfoCollector(
    private val lp: HumanoidListProvider,
    private val ip: HumanoidInfoProvider,
) {

    data class RequestProcessingState(
        val duration: SongsOfSyx.Duration,
        val since: SongsOfSyx.Instant,
        val result: CompletableFuture<Map<HumanoidInfoProvider.AI, Double>>,

        val state: MutableMap<HumanoidInfoProvider.AI, Double> = mutableMapOf(),
    )

    private val mu = ReentrantLock(true)
    private val requests = mutableMapOf<String, RequestProcessingState>()


    @OptIn(ExperimentalStdlibApi::class)
    fun submit(d: SongsOfSyx.Duration): Future<Map<HumanoidInfoProvider.AI, Double>> {
        Log.println("OccupationInfoCollector submit: $d")

        mu.lock()
        val id = Random.Default.nextBytes(16).toHexString()
        val f = CompletableFuture<Map<HumanoidInfoProvider.AI, Double>>()
        requests[id] = RequestProcessingState(d, SongsOfSyx.Instant(TIME.currentSecond()), f)
        mu.unlock()

        return f
    }

    fun update(d: Double) {
        val hs = lp.getHumanoids().let { hs ->
            if (hs is Either.Right) {
                Log.println("OccupationInfoCollector update: ${hs.value.message}")
                return
            }
            (hs as Either.Left).value
        }

        mu.locked {
            for (h in hs) {
                val ai = ip.ai(h)
                for (item in requests) {
                    item.value.state.compute(ai) { _, v ->
                        (v ?: 0.0) + d
                    }
                }
            }

            val toBeRemoved = mutableSetOf<String>()
            for (r in requests) {
                val delta = SongsOfSyx.Duration(SongsOfSyx.Instant.now(), r.value.since)

                if (delta > r.value.duration) {
                    r.value.result.complete(r.value.state)
                    toBeRemoved.add(r.key)
                }
            }
            requests.keys.removeAll(toBeRemoved)
        }
    }
}

object SongsOfSyx {
    data class Instant(val seconds: Double) {
        companion object {
            fun now() = Instant(TIME.currentSecond())
        }
    }

    class Time(val i: Instant) {
        val second: Int
            get() = i.seconds.toInt()
        val hour: Int
            get() = second / TIME.secondsPerHour
        val day: Int
            get() = second / TIME.secondsPerDay
        val season: Int
            get() = second / TIME.days().cycleSeconds().toInt()
        val year: Int
            get() = second / TIME.seasons().cycleSeconds().toInt()
    }

    class Duration(val seconds: Double) {

        constructor(i1: Instant, i2: Instant) : this(i2.seconds - i1.seconds)

        val hours: Double
            get() = seconds / TIME.secondsPerHour
        val days: Double
            get() = seconds / TIME.secondsPerDay
        val seasons: Double
            get() = seconds / TIME.days().cycleSeconds()
        val years: Double
            get() = seconds / TIME.seasons().cycleSeconds()

        operator fun compareTo(duration: SongsOfSyx.Duration): Int = seconds.compareTo(duration.seconds)

        companion object {
            fun years(y: Double) = Duration(y * TIME.seasons().cycleSeconds())
            fun seasons(s: Double) = Duration(s * TIME.days().cycleSeconds())
            fun days(d: Double) = Duration(d * TIME.secondsPerDay)
            fun hours(h: Double) = Duration(h * TIME.secondsPerHour)
        }
    }
}

private fun <R> ReentrantLock.locked(function: () -> R): R {
    lock()
    try {
        return function()
    } finally {
        unlock()
    }
}
