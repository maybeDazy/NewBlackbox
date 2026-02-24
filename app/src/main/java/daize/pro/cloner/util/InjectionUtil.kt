package daize.pro.cloner.util

import daize.pro.cloner.data.AppsRepository
import daize.pro.cloner.data.FakeLocationRepository
import daize.pro.cloner.data.GmsRepository

import daize.pro.cloner.view.apps.AppsFactory
import daize.pro.cloner.view.fake.FakeLocationFactory
import daize.pro.cloner.view.gms.GmsFactory
import daize.pro.cloner.view.list.ListFactory



object InjectionUtil {

    private val appsRepository = AppsRepository()



    private val gmsRepository = GmsRepository()

    private val fakeLocationRepository = FakeLocationRepository()

    fun getAppsFactory() : AppsFactory {
        return AppsFactory(appsRepository)
    }

    fun getListFactory(): ListFactory {
        return ListFactory(appsRepository)
    }


    fun getGmsFactory():GmsFactory{
        return GmsFactory(gmsRepository)
    }

    fun getFakeLocationFactory():FakeLocationFactory{
        return FakeLocationFactory(fakeLocationRepository)
    }
}