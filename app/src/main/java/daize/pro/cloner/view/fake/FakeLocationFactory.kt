package daize.pro.cloner.view.fake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import daize.pro.cloner.data.FakeLocationRepository


class FakeLocationFactory(private val repo: FakeLocationRepository) :
    ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FakeLocationViewModel(repo) as T
    }
}