package daize.pro.cloner.view.gms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import daize.pro.cloner.data.GmsRepository


class GmsFactory(private val repo:GmsRepository): ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GmsViewModel(repo) as T
    }
}