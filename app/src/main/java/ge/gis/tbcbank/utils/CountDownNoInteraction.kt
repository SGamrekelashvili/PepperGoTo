package ge.gis.tbcbank.utils

import android.os.CountDownTimer
import android.util.Log
import androidx.fragment.app.Fragment
import ge.gis.tbcbank.MainActivity

class CountDownNoInteraction(
    private val mainActivity: MainActivity,
    private val fragment: Fragment,
    millisUtilEnd: Long,
    countDownInterval: Long
) :
    CountDownTimer(millisUtilEnd, countDownInterval) {
    private val TAG = "MSI_NoInteraction"
    override fun onTick(millisUntilFinished: Long) {
        //Log.d(TAG,"Millis until end : " + millisUntilFinished);
    }

    override fun onFinish() {
        Log.d("morchibazarsaaa?", "Timer Finished")

        mainActivity.setFragment(fragment)

        mainActivity.runOnUiThread {

            mainActivity.goToRandomLocation(true)
        }

    }

    fun reset() {
        Log.d(TAG, "Timer Reset")
        super.cancel()
        super.start()
    }

}