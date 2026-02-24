package daize.pro.cloner.view.list

import androidx.lifecycle.MutableLiveData
import daize.pro.cloner.bean.InstalledAppBean
import daize.pro.cloner.data.AppsRepository
import daize.pro.cloner.view.base.BaseViewModel


class ListViewModel(private val repo: AppsRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<InstalledAppBean>>()

    val loadingLiveData = MutableLiveData<Boolean>()

    fun previewInstalledList() {
        launchOnUI { repo.previewInstallList() }
    }

    fun getInstallAppList(userID: Int) {
        launchOnUI { repo.getInstalledAppList(userID, loadingLiveData, appsLiveData) }
    }
}
