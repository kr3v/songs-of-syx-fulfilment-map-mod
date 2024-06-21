package kr3v.songsofsyx.fulfilmentprovider.script

import game.time.TIME
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr3v.songsofsyx.fulfilmentprovider.script.Constants.PORT
import script.SCRIPT
import script.SCRIPT.SCRIPT_INSTANCE
import settlement.entity.humanoid.Humanoid
import settlement.main.SETT
import settlement.stats.STATS
import snake2d.util.datatypes.COORDINATE
import snake2d.util.file.FileGetter
import snake2d.util.file.FilePutter
import snake2d.util.sprite.text.Str
import java.util.concurrent.locks.ReentrantLock


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

class FulfilmentProviderScriptInstance : SCRIPT_INSTANCE {

    internal val stateLock = ReentrantLock()
    internal lateinit var state: List<HumanoidRecord>

    fun isStateInitialized() = (::state).isInitialized

    init {
        Log.println("FulfilmentProviderScriptInstance created")
    }

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
            .filter { e -> e.indu().hType().player && e.indu().popCL().cl.key != "CHILD" }.map(
                FulfilmentProviderScriptInstance::HumanoidRecord
            )

        stateLock.lock()
        state = stateUpd
        stateLock.unlock()
    }

    data class HumanoidRecord(val h: Humanoid) {
        val pos: COORDINATE
            get() = h.tc()

        data class ServiceAccess(
            val total: Double,
            val access: Double,
            val proximity: Double,
            val quality: Double,
        )

        val serviceAccess: Map<Services.Type, ServiceAccess>
            get() = (Services.services).mapValues { (_, service) ->
                ServiceAccess(
                    service.total(h), if (service.access(h)) 1.0 else 0.0, service.proximity(h), service.quality(h)
                )
            }


        data class BasicAI(
            val destination: COORDINATE,
            val resourceCarried: String,
            val resourceA: Int,
            val occupation: String,
        )

        val basicAI: BasicAI
            get() = BasicAI(
                destination = h.ai().destination,
                resourceCarried = h.ai().resourceCarried().name.toString(),
                resourceA = h.ai().resourceA(),
                occupation = Str("").also { h.ai().getOccupation(h, it) }.toString()
            )
    }

    override fun save(p0: FilePutter?) {
    }

    override fun load(p0: FileGetter?) {
    }
}