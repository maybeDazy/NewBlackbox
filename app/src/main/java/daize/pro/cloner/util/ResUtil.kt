package daize.pro.cloner.util

import androidx.annotation.StringRes
import daize.pro.cloner.app.App


fun getString(@StringRes id:Int,vararg arg:String):String{
    if(arg.isEmpty()){
        return App.getContext().getString(id)
    }
    return App.getContext().getString(id,*arg)
}

