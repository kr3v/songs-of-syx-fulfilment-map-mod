package kr3v

import game.time.TIME
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr3v.Constants.PORT
import script.SCRIPT
import script.SCRIPT.SCRIPT_INSTANCE
import settlement.entity.humanoid.Humanoid
import settlement.main.SETT
import settlement.stats.STATS
import snake2d.util.datatypes.COORDINATE
import snake2d.util.file.FileGetter
import snake2d.util.file.FilePutter
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO

object Constants {
    const val MOD_NAME = "Fulfilment Provider"
    const val MOD_VERSION = "0.0.1"
    const val PORT = 63481
    const val MOD_DESC = "Fulfilment Provider"

    val log = PrintWriter(FileWriter("/home/dbaynak/.local/share/songsofsyx/logs/fulfilment-provider.log"))
}

class FulfilmentProviderScript : SCRIPT {
    internal lateinit var instance: FulfilmentProviderScriptInstance
    private val srv: FulfilmentProviderServer = FulfilmentProviderServer(this)

    fun hasInstance() = (::instance).isInitialized

    init {
        Constants.log.println("FulfilmentProviderScript created")
        Constants.log.flush()

        srv.route(PORT)
    }

    override fun name(): CharSequence {
        return "Fulfilment Provider"
    }

    override fun desc(): CharSequence {
        return "Fulfilment Provider at $PORT"
    }

    override fun createInstance(): SCRIPT_INSTANCE {
        Constants.log.println("FulfilmentProviderScript createInstance")
        Constants.log.flush()

        val v = FulfilmentProviderScriptInstance()
        instance = v
        return v
    }

    override fun forceInit(): Boolean {
        return true
    }
}

/*
TODO:
- add shrines, temples
- check other services
- render service providers (rooms) on the map
- fix startup when script is not loaded for a map
 */

class FulfilmentProviderServer(private val script: FulfilmentProviderScript) {
    internal fun route(port: Int) {
        embeddedServer(CIO, port = port) {
            routing {
                get("/{service}.png") {
                    val param = call.parameters["service"]
                    if (param.isNullOrEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "Missing name parameter")
                        return@get
                    }

                    if (!script.hasInstance()) {
                        call.respond(HttpStatusCode.BadRequest, "Script instance not initialized")
                        return@get
                    }

                    if (!script.instance.isStateInitialized()) {
                        call.respond(HttpStatusCode.BadRequest, "State not initialized")
                        return@get
                    }

                    script.instance.stateLock.lock()
                    val st = script.instance.state
                    script.instance.stateLock.unlock()

                    val service = st[FulfilmentProviderScriptInstance.ServiceType.valueOf(param)]
                    if (service == null) {
                        call.respond(HttpStatusCode.BadRequest, "Service not found")
                        return@get
                    }


                    call.response.header("Content-Type", "image/png")
                    call.response.header("Content-Disposition", "inline; filename=\"hello.png\"")


                    val img = img(service)

                    call.respondOutputStream {
                        ImageIO.write(img, "png", BufferedOutputStream(this))
                    }
                }
            }
        }.start(wait = false)
    }

    private fun img(st: Map<COORDINATE, List<FulfilmentProviderScriptInstance.Record>>): BufferedImage {
        val img = BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB)
        val g = img.graphics
        st.forEach { (k, v) ->
            val r = v.reduce { acc, record ->
                acc.copy(
                    total = acc.total + record.total,
                    access = acc.access + record.access,
                    proximity = acc.proximity + record.proximity,
                    quality = acc.quality + record.quality
                )
            }
            val rnorm = r.copy(
                total = r.total / v.size,
                access = r.access / v.size,
                proximity = r.proximity / v.size,
                quality = r.quality / v.size
            )

            val x = k.x()
            val y = k.y()

            (rnorm.total * 255).toInt().let { green ->
                g.color = java.awt.Color(255 - green, green, 0)
                g.fillRect(x * 2, y * 2, 2, 2)
            }
            (rnorm.access * 255).toInt().let { green ->
                g.color = java.awt.Color(255 - green, green, 0)
                g.fillRect(x * 2 + 2, y * 2, 2, 2)
            }
            (rnorm.proximity * 255).toInt().let { green ->
                g.color = java.awt.Color(255 - green, green, 0)
                g.fillRect(x * 2 + 2, y * 2 + 2, 2, 2)
            }
            (rnorm.quality * 255).toInt().let { green ->
                g.color = java.awt.Color(255 - green, green, 0)
                g.fillRect(x * 2, y * 2 + 2, 2, 2)
            }

        }
        return img
    }
}

class FulfilmentProviderScriptInstance : SCRIPT_INSTANCE {

    data class Record(val total: Double, val access: Double, val proximity: Double, val quality: Double)

    internal val stateLock = ReentrantLock()
    internal lateinit var state: Map<ServiceType, Map<COORDINATE, List<Record>>>

    fun isStateInitialized() = (::state).isInitialized

    init {
        Constants.log.println("FulfilmentProviderScriptInstance created")
        Constants.log.flush()
    }

    private var lastDays = -1

    enum class ServiceType {
        HEARTH,
        BATH,
        WELL,
        BARBER,
        BROTHEL,
        LAVATORY,
        PHYSICIAN,
        SPEAKER,
        STAGE,
        ARENA,
        GARENA,
        CANTEEN,
        EATERY,
        TAVERN,
        MARKET
    }

    private val services by lazy {
        mapOf(
            ServiceType.HEARTH to (STATS.SERVICE().get(SETT.ROOMS().HEARTH.service())),
            ServiceType.BATH to (STATS.SERVICE().get(SETT.ROOMS().BATHS.get(0).service())),
            ServiceType.WELL to (STATS.SERVICE().get(SETT.ROOMS().WELLS.get(0).service())),
            ServiceType.BARBER to (STATS.SERVICE().get(SETT.ROOMS().BARBERS.get(0).service())),
            ServiceType.BROTHEL to (STATS.SERVICE().get(SETT.ROOMS().BROTHELS.get(0).service())),
            ServiceType.LAVATORY to (STATS.SERVICE().get(SETT.ROOMS().LAVATORIES.get(0).service())),
            ServiceType.PHYSICIAN to (STATS.SERVICE().get(SETT.ROOMS().PHYSICIANS.get(0).service())),
            ServiceType.SPEAKER to (STATS.SERVICE().get(SETT.ROOMS().SPEAKERS.get(0).service())),
            ServiceType.STAGE to (STATS.SERVICE().get(SETT.ROOMS().STAGES.get(0).service())),
            ServiceType.ARENA to (STATS.SERVICE().get(SETT.ROOMS().ARENAS.get(0).service())),
            ServiceType.GARENA to (STATS.SERVICE().get(SETT.ROOMS().GARENAS.get(0).service())),
            ServiceType.CANTEEN to (STATS.SERVICE().get(SETT.ROOMS().CANTEENS.get(0).service())),
            ServiceType.EATERY to (STATS.SERVICE().get(SETT.ROOMS().EATERIES.get(0).service())),
            ServiceType.TAVERN to (STATS.SERVICE().get(SETT.ROOMS().TAVERNS.get(0).service())),
            ServiceType.MARKET to (STATS.SERVICE().get(SETT.ROOMS().MARKET.get(0).service()))
        )
    }


    override fun update(d: Double) {
        if (d <= 1e-6) {
            return
        }

        val currDay = TIME.days().bitsSinceStart()
        if (currDay == lastDays) {
            return
        }
        lastDays = currDay

        Constants.log.println("FulfilmentProviderScriptInstance update: d=$d, currDay=$currDay, lastDays=$lastDays")
        Constants.log.flush()

        val ents = SETT.ENTITIES()
        val stateUpd = services.map { it }
            .associate { (type, service) ->
                val vals: Map<COORDINATE, List<Record>> = ents.allEnts
                    .mapNotNull { e -> e as? Humanoid }
                    .filter { e -> e.indu().hType().player && e.indu().popCL().cl.key != "CHILD" }
                    .groupBy(Humanoid::tc) { e ->
                        Record(
                            service.total(e),
                            if (service.access(e)) 1.0 else 0.0,
                            service.proximity(e),
                            service.quality(e)
                        )
                    }

                type to vals
            }

        stateLock.lock()
        state = stateUpd
        stateLock.unlock()
    }

    override fun save(p0: FilePutter?) {
    }

    override fun load(p0: FileGetter?) {
    }
}