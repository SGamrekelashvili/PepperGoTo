package ge.gis.tbcbank.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ge.gis.tbcbank.MainActivity
import ge.gis.tbcbank.R


class SplashFragment : Fragment() {
    private val TAG = "MSI_SplashFragment"
    private var ma: MainActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val fragmentId = R.layout.fragment_splash
        ma = activity as MainActivity?
        return if (ma != null) {
            val themeId: Int? = ma!!.getThemeId()
            if (themeId != null) {
                val contextThemeWrapper: Context =
                    ContextThemeWrapper(ma, themeId)
                val localInflater =
                    inflater.cloneInContext(contextThemeWrapper)
                localInflater.inflate(fragmentId, container, false)
            } else {
                inflater.inflate(fragmentId, container, false)
            }
        } else {
            Log.e(TAG, "could not get mainActivity, can't create fragment")
            null
        }
    }
}