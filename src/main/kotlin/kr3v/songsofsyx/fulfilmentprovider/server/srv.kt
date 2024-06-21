package kr3v.songsofsyx.fulfilmentprovider.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kr3v.songsofsyx.fulfilmentprovider.script.FulfilmentProviderScript
import kr3v.songsofsyx.fulfilmentprovider.script.FulfilmentProviderScriptInstance
import kr3v.songsofsyx.fulfilmentprovider.script.Services
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import javax.imageio.ImageIO


class Server(private val script: FulfilmentProviderScript) {
    internal fun route(port: Int) {
        embeddedServer(CIO, port = port) {
            routing {
                getServiceAccess()
            }
        }.start(wait = false)
    }

    private fun Routing.getServiceAccess() {
        get("/service-access/{service}.png") {
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

            val serviceType = try {
                Services.Type.valueOf(param)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Service not found")
                return@get
            }


            call.response.header("Content-Type", "image/png")
            call.response.header("Content-Disposition", "inline; filename=\"hello.png\"")


            val img = serviceAccessBuildImage(service)

            call.respondOutputStream {
                ImageIO.write(img, "png", BufferedOutputStream(this))
            }
        }
    }

    private fun serviceAccessBuildImage(st: List<FulfilmentProviderScriptInstance.HumanoidRecord>): BufferedImage {
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