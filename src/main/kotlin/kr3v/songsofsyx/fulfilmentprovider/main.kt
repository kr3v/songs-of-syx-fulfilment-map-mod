package kr3v.songsofsyx.fulfilmentprovider

import kr3v.songsofsyx.fulfilmentprovider.script.FulfilmentProviderScriptInstance
import kr3v.songsofsyx.fulfilmentprovider.script.HumanoidInfoProvider
import kr3v.songsofsyx.fulfilmentprovider.script.HumanoidListProvider
import kr3v.songsofsyx.fulfilmentprovider.script.OccupationInfoCollector
import kr3v.songsofsyx.fulfilmentprovider.server.Server
import script.SCRIPT
import script.SCRIPT.SCRIPT_INSTANCE


class FulfilmentProviderScript : SCRIPT {
    lateinit var instance: FulfilmentProviderScriptInstance
    lateinit var infoProvider: HumanoidInfoProvider
    lateinit var listProvider: HumanoidListProvider
    lateinit var occupationInfoCollector: OccupationInfoCollector

    private val srv: Server = Server(this)

    fun hasInstance() = (::instance).isInitialized

    init {
        Log.println("FulfilmentProviderScript created")
        srv.route(Config.PORT)
    }

    override fun name() = Config.MOD_NAME

    override fun desc() = Config.MOD_DESC

    override fun createInstance(): SCRIPT_INSTANCE {
        Log.println("FulfilmentProviderScript createInstance")
        val v = FulfilmentProviderScriptInstance()
        instance = v
        infoProvider = HumanoidInfoProvider()
        listProvider = instance
        occupationInfoCollector = OccupationInfoCollector(listProvider, infoProvider)
        instance.occInfoCollector = occupationInfoCollector
        return v
    }

    override fun forceInit() = true
}
