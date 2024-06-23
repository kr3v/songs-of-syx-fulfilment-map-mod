package kr3v.songsofsyx.fulfilmentprovider.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kr3v.songsofsyx.fulfilmentprovider.Either
import kr3v.songsofsyx.fulfilmentprovider.FulfilmentProviderScript
import kr3v.songsofsyx.fulfilmentprovider.script.HumanoidInfoProvider
import kr3v.songsofsyx.fulfilmentprovider.script.Services
import settlement.entity.humanoid.Humanoid
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import javax.imageio.ImageIO


class Server(private val script: FulfilmentProviderScript) {

    private val srv = HumanoidInfoProvider()

    internal fun route(port: Int) {
        embeddedServer(CIO, port = port) {
            routing {
                get("/service-access/{service}.png") {
                    serviceAccessImpl()
                }
                get("/occupations-stats") {
                    occupationStatsImpl()
                }

                get("/") {
                    call.respondText("Hello, world!")
                }

            }
        }.start(wait = false)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.occupationStatsImpl() {
        val st = when (val s = script.listProvider.getHumanoids()) {
            is Either.Right -> {
                call.respond(HttpStatusCode.BadRequest, s.value.message)
                return
            }

            is Either.Left -> s.value
        }

        val csvWriter = StringBuilder()
        fun appendRow(vararg values: Any) {
            csvWriter.append(values.joinToString(",")).append("\n")
        }

        appendRow("Occupation", "Resource Amount", "Resource Carried", "State", "Plan")
        for (h in st.reversed()) {
            val b = srv.ai(h)
            appendRow(b.occupation, b.resourceA, b.resourceCarried, b.state, b.plan)
        }

        call.respondText(csvWriter.toString(), contentType = ContentType.Text.CSV, status = HttpStatusCode.OK)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.serviceAccessImpl() {
        val param = call.parameters["service"]
        if (param.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing name parameter")
            return
        }

        val st = when (val s = script.listProvider.getHumanoids()) {
            is Either.Right -> {
                call.respond(HttpStatusCode.BadRequest, s.value.message)
                return
            }

            is Either.Left -> s.value
        }


        val serviceType = try {
            Services.Type.valueOf(param)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, "Service not found")
            return
        }


        call.response.header("Content-Type", "image/png")
        call.response.header("Content-Disposition", "inline; filename=\"hello.png\"")

        val img = serviceAccessBuildImage(st, serviceType)

        call.respondOutputStream {
            ImageIO.write(img, "png", BufferedOutputStream(this))
        }
    }

    private fun serviceAccessBuildImage(
        st: List<Humanoid>,
        serviceType: Services.Type,
    ): BufferedImage {
        val img = BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB)
        val g = img.graphics

        val posToAccess = st.groupBy({ srv.pos(it) }) { srv.servicesAccess(it)[serviceType]!! }

        posToAccess.forEach { (k, v) ->
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