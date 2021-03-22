package ge.gis.tbcbank

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import com.aldebaran.qi.Consumer
import com.aldebaran.qi.Function
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.TransformTime
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.`object`.humanawareness.HumanAwareness
import com.aldebaran.qi.sdk.`object`.streamablebuffer.StreamableBuffer
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoTo
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.await
import ge.gis.tbcbank.fragments.MainFragment
import ge.gis.tbcbank.fragments.SplashFragment
import ge.gis.tbcbank.utils.CountDownNoInteraction
import ge.gis.tbcbank.utils.SaveFileHelper
import ge.gis.tbcbank.utils.TopicChatBuilder
import ge.gis.tbcbank.utils.Vector2theta
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


class MainActivity : RobotActivity(), RobotLifecycleCallbacks {
    private var nextLocation: String? = null
    var goToRandomRunning = false
    private var streamableExplorationMap: StreamableBuffer? = null
    private var toSaveUpdatedExplorationMap: ExplorationMap? = null
    var readedExplorationMap: ExplorationMap? = null
    var builtLocalize: Localize? = null
    var explorationMap: ExplorationMap? = null
    lateinit var currentlyRunningLocalize: Future<Void>
    private var currentGoToAction: Future<Boolean>? = null

    var progressBarForMapDialog: Dialog? = null

    private var countDownNoInteraction: CountDownNoInteraction? = null
    private var humanAwareness: HumanAwareness? = null

    private var currentFragment: String? = null
    private var fragmentManager: FragmentManager? = null


    private var TAG = "DDDD"
    private lateinit var spinnerAdapter: ArrayAdapter<String>
    private var selectedLocation: String? = null
    private var qiContext: QiContext? = null
    private var actuation: Actuation? = null
    private var mapping: Mapping? = null
    private var initialExplorationMap: ExplorationMap? = null
    private var holder: Holder? = null
    var publishExplorationMapFuture: Future<Void>? = null
    private val MULTIPLE_PERMISSIONS = 2
    var saveFileHelper: SaveFileHelper? = null
    val filesDirectoryPath = "/sdcard/Maps"
    val mapFileName = "mapData.txt"
    private val locationsFileName = "points.json"
    private val load_location_success =
        AtomicBoolean(false)

    private var savedLocations: MutableMap<String, AttachedFrame> = mutableMapOf()


    var goToFuture: Future<Void>? = null
    private var goTo: GoTo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        QiSDK.register(this, this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        this.fragmentManager = supportFragmentManager

//        countDownNoInteraction = CountDownNoInteraction(
//            this, MainFragment(),
//            30000, 10000
//        )
//        countDownNoInteraction!!.start()
//
//        setFragment(MainFragment())
//

        saveFileHelper = SaveFileHelper()
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            QiSDK.register(this, this)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,),
                MULTIPLE_PERMISSIONS
            )
        }

        save_button.setOnClickListener {
            val location: String = add_item_edit.text.toString()
            add_item_edit.text.clear()
            // Save location only if new.
            if (location.isNotEmpty() && !savedLocations.containsKey(location)) {
                spinnerAdapter.add(location)
                saveLocation(location)


            }
        }


        goToRandom.setOnClickListener {
            if (savedLocations.isNotEmpty()){

            goToRandomLocation(true)
            countDownNoInteraction = CountDownNoInteraction(
                this, SplashFragment(),
                20000, 10000
            )
            countDownNoInteraction!!.start()

            setFragment(SplashFragment())
            explorationMapView.visibility = View.GONE
            }else{
                runOnUiThread {
                    Toast.makeText(this, "get all", Toast.LENGTH_SHORT).show()
                }
            }


        }

        loadMap.setOnClickListener {

            buildStreamableExplorationMap()

        }

        localizeHimself.setOnClickListener {


            holdAbilities(true)!!.andThenConsume {
                Log.d(TAG, "holded")


                animationToLookInFront()!!.andThenConsume {
                    localize()

                }


            }.andThenConsume {

                Log.d(TAG, "released")
                releaseAbilities()
            }


        }

        get.setOnClickListener {

            loadLocations()

        }

        goto_button.setOnClickListener {

            goToFuture!!.requestCancellation()

//            selectedLocation?.let {
//                goto_button.isEnabled = false
//                save_button.isEnabled = false
//                val thread = Thread {
//                    goToLocation(it, OrientationPolicy.ALIGN_X)
//                }
//                thread.start()
//            }

        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                selectedLocation = parent.getItemAtPosition(position) as String
                Log.i("TAG", "onItemSelected: $selectedLocation")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedLocation = null
                Log.i("TAG", "onNothingSelected")
            }
        }

        spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ArrayList())
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter


        loadLocations()



        sTopLocalization.setOnClickListener {


            publishExplorationMapFuture?.cancel(true)
            releaseAbilities()

            Thread {


                Log.d("awddwa", toSaveUpdatedExplorationMap.toString())

                setStreamableMap(toSaveUpdatedExplorationMap!!.serializeAsStreamableBuffer())

                val mapData: StreamableBuffer? = getStreamableMap()
                saveFileHelper!!.writeStreamableBufferToFile(
                    filesDirectoryPath,
                    mapFileName,
                    mapData!!

                )

            }.start()

        }

        extendMapButton.setOnClickListener {
            // Check that an initial map is available.
            val initialExplorationMap = initialExplorationMap ?: return@setOnClickListener
            // Check that the Activity owns the focus.
            val qiContext = qiContext ?: return@setOnClickListener
            // Start the map extension step.
            startMapExtensionStep(initialExplorationMap, qiContext)

            releaseAbilities()

        }

        startMappingButton.setOnClickListener {
            // Check that the Activity owns the focus.
            val qiContext = qiContext ?: return@setOnClickListener
            // Start the mapping step.
            startMappingStep(qiContext)
        }


    }

    fun showProgress(mContext: Context?) {
        progressBarForMapDialog = Dialog(mContext!!)
        progressBarForMapDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        progressBarForMapDialog!!.setContentView(R.layout.progress_bar)
        progressBarForMapDialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        progressBarForMapDialog!!.setCancelable(false)
        progressBarForMapDialog!!.setCanceledOnTouchOutside(false)
        progressBarForMapDialog!!.show()
    }


    fun hideProgress() {
        if (progressBarForMapDialog != null) {
            progressBarForMapDialog!!.dismiss()
            progressBarForMapDialog = null;
        }
    }


    fun buildStreamableExplorationMap(): Future<ExplorationMap>? {


        if (getStreamableMap() == null) {

            showProgress(this)

            val mapData = saveFileHelper!!.readStreamableBufferFromFile(
                filesDirectoryPath,
                mapFileName
            )


            setStreamableMap(mapData!!)


            Thread {


                readedExplorationMap = ExplorationMapBuilder.with(qiContext)
                    .withStreamableBuffer(streamableExplorationMap).build()

                mapToBitmap(readedExplorationMap!!)

                runOnUiThread {
                    hideProgress()
                }


            }.start()

        }


        if (explorationMap == null) {
            Log.d(
                TAG,
                "buildStreamableExplorationMap: Building map from StreamableBuffer"
            )
            return ExplorationMapBuilder.with(qiContext)
                .withStreamableBuffer(streamableExplorationMap).buildAsync()
        }
        return Future.of(explorationMap)
    }


    fun localize() {

        LocalizeBuilder.with(qiContext).withMap(readedExplorationMap).buildAsync()
            .andThenCompose { localize: Localize ->
                builtLocalize = localize
                Log.d(
                    TAG,
                    "localize: localize built successfully"
                )
                Log.d(
                    TAG,
                    "localize: addOnStatusChangedListener"
                )

                currentlyRunningLocalize = builtLocalize!!.async().run()
                Log.d(
                    TAG,
                    "localize running..."
                )

                runOnUiThread {

                    localizeHimself.text = "success"
                }


                currentlyRunningLocalize


            }
            .thenConsume { finishedLocalize: Future<Void> ->
                Log.d(
                    TAG,
                    "localize: removeAllOnStatusChangedListeners"
                )
                builtLocalize!!.removeAllOnStatusChangedListeners()


                if (finishedLocalize.isCancelled) {
                    Log.d(
                        TAG,
                        "localize cancelled."
                    )
                } else if (finishedLocalize.hasError()) {
                    Log.d(
                        TAG,
                        "Failed to localize in map : ",
                        finishedLocalize.error
                    )
                    //The error below could happen when trying to run multiple Localize action with the same Localize object (called builtLocalize here).
                    if (finishedLocalize.error
                            .toString() == "com.aldebaran.qi.QiException: tr1::bad_weak_ptr" || finishedLocalize.error
                            .toString() == "com.aldebaran.qi.QiException: Animation failed."
                    ) {
                        Log.d(
                            TAG,
                            "localize: com.aldebaran.qi.QiException: tr1::bad_weak_ptr"
                        )
                        builtLocalize = null
                    } else {
                        Log.d(
                            TAG,
                            "Failed"
                        )
                    }
                } else {
                    Log.d(
                        TAG,
                        "localize finished."
                    )
                }


            }.andThenConsume {
                runOnUiThread {

                    localizeHimself.text = "failed"
                }
            }

    }

//    private fun checkAndCancelCurrentLocalize(): Future<Void> {
//        if (currentlyRunningLocalize == null) return Future.of(
//            null
//        )
//        currentlyRunningLocalize.requestCancellation()
//        Log.d(
//            TAG,
//            "checkAndCancelCurrentLocalize"
//        )
//        return currentlyRunningLocalize
//    }

    fun createAttachedFrameFromCurrentPosition(): Future<AttachedFrame>? {
        return actuation!!.async()
            .robotFrame()
            .andThenApply { robotFrame: Frame ->
                val mapFrame: Frame = mapping!!.async().mapFrame().value;


                val transformTime: TransformTime = robotFrame.computeTransform(mapFrame)
                mapFrame.makeAttachedFrame(transformTime.transform)
            }
    }

    fun saveLocation(location: String?) {
        // Get the robot frame asynchronously.
        Log.d(TAG, "saveLocation: Start saving this location")



        createAttachedFrameFromCurrentPosition()?.andThenConsume {


                attachedFrame ->
            savedLocations[location!!] = attachedFrame

            backupLocations()

        }
    }

    private fun getMapFrame(): Frame? {
        return mapping!!.async().mapFrame().value
    }

    private fun backupLocations() {

        val locationsToBackup = TreeMap<String, Vector2theta>()
        val mapFrame: Frame = getMapFrame()!!
        for ((key, destination) in savedLocations) {
            // get location of the frame
            Log.d(
                "sdsdsdsd", destination.toString()
            )
            val frame = destination.async().frame().value

            // create a serializable vector2theta
            val vector = Vector2theta.betweenFrames(mapFrame, frame)

            // add to backup list
            locationsToBackup[key] = vector
        }
        SaveFileHelper().saveLocationsToFile(
            filesDirectoryPath,
            locationsFileName,
            locationsToBackup
        )

    }

    fun loadLocations(): Future<Boolean>? {
        return FutureUtils.futureOf<Boolean> { f: Future<Void?>? ->
            val file =
                File(filesDirectoryPath, locationsFileName)
            if (file.exists()) {
                val vectors: MutableMap<String?, Vector2theta?>? =
                    saveFileHelper!!.getLocationsFromFile(
                        filesDirectoryPath,
                        locationsFileName
                    )

                // Clear current savedLocations.
                savedLocations = TreeMap()
                val mapFrame: Frame? =
                    getMapFrame()


                Log.i("yvelaa", vectors.toString())

                vectors!!.forEach { (key1, value1) ->

                    val t = value1!!.createTransform()
                    Log.d(TAG, "loadLocations: $key1")

                    runOnUiThread {
                        spinnerAdapter.add(key1)
                    }


                    val attachedFrame =
                        mapFrame!!.async().makeAttachedFrame(t).value

                    // Store the FreeFrame.
                    savedLocations[key1!!] = attachedFrame

                }

                load_location_success.set(true)


                runOnUiThread {

                    Toast.makeText(this, savedLocations.toString(), Toast.LENGTH_LONG).show()
                }

                Log.d(TAG, "loadLocations: Done")
                Log.d(TAG, savedLocations.toString())
                if (load_location_success.get()) return@futureOf Future.of(
                    true
                ) else throw Exception("Empty file")
            } else {
                throw Exception("No file")
            }
        }
    }


    fun goToRandomLocation(setGoToRandom: Boolean) {
        goToRandomRunning = setGoToRandom
        if(goToRandomRunning){
            nextLocation = pickRandomLocation()
            thread {
                goToLocation(nextLocation!!, OrientationPolicy.ALIGN_X)
            }
        }else{
            Log.d("STOP RANDOM","STOPPED")
        }
//        if (goToRandomRunning) {
//            goToRandomFuture = FutureUtils.wait(10, TimeUnit.SECONDS)
//                .andThenConsume { aUselessFuture: Void? ->
//                    goToRandomLocation(
//                        goToRandomRunning
//                    )
//                }
//            nextLocation = pickRandomLocation()
//            thread {
//
//                goToLocation(nextLocation!!, OrientationPolicy.ALIGN_X)
//            }
//
//        } else {
//                goToRandomFuture!!.requestCancellation()
//                goToRandomFuture!!.cancel(true)
//            }
    }


    private fun pickRandomLocation(): String {
        val keysAsArray: MutableList<String> = java.util.ArrayList(savedLocations.keys)
        val r = Random()
        val location = keysAsArray[r.nextInt(keysAsArray.size)]
        return if (location != nextLocation) {
            location
        } else pickRandomLocation()
    }

    private fun goToLocation(location: String, orientationPolicy: OrientationPolicy) {

        Log.d("awdawdawd", location)

        val currentGoToLocation = location

        val freeFrame: AttachedFrame? = savedLocations[currentGoToLocation]
//        val frameFuture: Frame = freeFrame!!.frame()
        goTo = GoToBuilder.with(qiContext)
            .withFrame(freeFrame!!.frame())
            .withMaxSpeed(0.3f)
            .build()
        goToFuture = goTo?.async()?.run()
        goToFuture!!.thenConsume { future ->
            when {
                future.isSuccess -> {
                    Log.i("GoTo Success", "GoTo action finished with success.")
                    goToRandomLocation(true)
                }
                future.isCancelled -> {
                    Log.i("GoTo Cancelled", "GoTo action finished with Cancelled.")
                    goToRandomLocation(false)
                }
                future.hasError() -> {
                    Log.i("GoTo hasError", "GoTo action finished with hasError. ${future.error}")
                    goToRandomLocation(true)
                }
                future.isDone -> {
                    Log.i("GoTo isDone", "GoTo action finished with isDone")
                    goToRandomLocation(true)
                }
                else -> {
                    Log.i("GoTo", "Else Unknown Error.")
                    goToRandomLocation(false)
                }
            }
        }

//        val appscope = SingleThread.newCoroutineScope()
//        currentGoToAction = appscope.asyncFuture {
//            goto = StubbornGoToBuilder.with(qiContext!!)
//                .withFinalOrientationPolicy(orientationPolicy)
//                .withMaxRetry(10)
//                .withMaxSpeed(0.25f)
//                .withMaxDistanceFromTargetFrame(0.3)
//                .withWalkingAnimationEnabled(true)
//                .withFrame(frameFuture).build()
//            currentGoToAction = goto!!.async().run()
//          }
//        runBlocking {
//            delay(5000)
//            future.requestCancellation()
//            future.awaitOrNull()
//            delay(5000)
//            goto!!.async().run().await()
//        }

        waitForInstructions()

    }


    fun checkAndCancelCurrentGoto(): Future<Boolean>? {
        if (currentGoToAction == null) {
            return Future.of(null)
        }
        currentGoToAction!!.requestCancellation()
//        currentGoToAction!!.cancel(true);
        goToRandomLocation(false)
        Log.d(TAG, "checkAndCancelCurrentGoto")
        return currentGoToAction
    }

    fun waitForInstructions() {
        Log.i("TAG", "Waiting for instructions...")
        runOnUiThread {
            save_button.isEnabled = true
            goto_button.isEnabled = true
        }
    }


    private fun mapSurroundings(qiContext: QiContext): Future<ExplorationMap> {
        // Create a Promise to set the operation state later.
        val promise = Promise<ExplorationMap>().apply {
            // If something tries to cancel the associated Future, do cancel it.
            setOnCancel {
                if (!it.future.isDone) {
                    setCancelled()
                }
            }
        }

        val localizeAndMapFuture = LocalizeAndMapBuilder.with(qiContext)
            .buildAsync()
            .andThenCompose { localizeAndMap ->
                localizeAndMap.addOnStatusChangedListener { status ->
                    if (status == LocalizationStatus.LOCALIZED) {
                        val explorationMap = localizeAndMap.dumpMap()

                        if (!promise.future.isDone) {
                            promise.setValue(explorationMap)
                        }
                    }
                }

                // Run the LocalizeAndMap.
                localizeAndMap.async().run()

                    .thenConsume {
                        // Remove the OnStatusChangedListener.
                        localizeAndMap.removeAllOnStatusChangedListeners()
                        // In case of error, forward it to the Promise.
                        if (it.hasError() && !promise.future.isDone) {
                            promise.setError(it.errorMessage)
                        }
                    }
            }

        // Return the Future associated to the Promise.
        return promise.future.thenCompose {
            // Stop the LocalizeAndMap.
            localizeAndMapFuture.cancel(true)
            return@thenCompose it
        }
    }

    private fun mapToBitmap(explorationMap: ExplorationMap) {
        explorationMapView.setExplorationMap(explorationMap.topGraphicalRepresentation)
    }

    fun getStreamableMap(): StreamableBuffer? {
        return streamableExplorationMap
    }

    fun setStreamableMap(map: StreamableBuffer) {
        streamableExplorationMap = map
    }

    private fun startMappingStep(qiContext: QiContext) {

        startMappingButton.isEnabled = false

        Log.i(TAG.toString(), "startMappingStep Class")
        // Map the surroundings and get the map.
        mapSurroundings(qiContext).thenConsume { future ->
            if (future.isSuccess) {
                Log.i(TAG, "FUTURE Success")
                val explorationMap = future.get()
                // Store the initial map.
                this.initialExplorationMap = explorationMap
                // Convert the map to a bitmap.
                mapToBitmap(explorationMap)
                // Display the bitmap and enable "extend map" button.


                runOnUiThread {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        extendMapButton.isEnabled = true
                    }
                }
            } else {
                // If the operation is not a success, re-enable "start mapping" button.
                runOnUiThread {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        startMappingButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun publishExplorationMap(
        localizeAndMap: LocalizeAndMap,
        updatedMapCallback: (ExplorationMap) -> Unit
    ): Future<Void> {
        return localizeAndMap.async().dumpMap().andThenCompose {
            Log.i(TAG, "$it Function")
            updatedMapCallback(it)
            FutureUtils.wait(1, TimeUnit.SECONDS)
        }.andThenCompose {
            publishExplorationMap(localizeAndMap, updatedMapCallback)
        }
    }

    private fun startMapExtensionStep(initialExplorationMap: ExplorationMap, qiContext: QiContext) {
        Log.i(TAG, "StartMapEXTENSION Class")
        extendMapButton.isEnabled = false
        holdAbilities(true)!!.andThenConsume {
            animationToLookInFront()!!.andThenConsume {
                extendMap(initialExplorationMap, qiContext) { updatedMap ->
                    explorationMapView.setExplorationMap(initialExplorationMap.topGraphicalRepresentation)
                    toSaveUpdatedExplorationMap = updatedMap
                    mapToBitmap(updatedMap)
                    // Display the bitmap.
                    runOnUiThread {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            robotOnMap(initialExplorationMap, qiContext)
                        }
                    }
                }.thenConsume { future ->
                    // If the operation is not a success, re-enable "extend map" button.
                    if (!future.isSuccess) {
                        runOnUiThread {
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                extendMapButton.isEnabled = true
                            }
                        }
                        releaseAbilities()
                    }
                }
            }
        }

    }


    private fun robotOnMap(initialExplorationMap: ExplorationMap, qiContext: QiContext) {
        Log.i(TAG.toString(), "$initialExplorationMap Class")

    }

    fun animationToLookInFront(): Future<Void?>? {
        Log.d(TAG, "animationToLookInFront: started")
        return AnimationBuilder.with(qiContext) // Create the builder with the context.
            .withResources(R.raw.idle) // Set the animation resource.
            .buildAsync()
            .andThenCompose(
                Function<Animation, Future<Void>> { animation: Animation? ->
                    AnimateBuilder.with(qiContext)
                        .withAnimation(animation)
                        .buildAsync()
                        .andThenCompose { animate: Animate -> animate.async().run() }
                }
            )
    }


    fun holdAbilities(withBackgroundMovement: Boolean): Future<Void?>? {
        return releaseAbilities()!!.thenCompose<Void>(
            Function<Future<Void?>, Future<Void>> { voidFuture: Future<Void?>? ->

                Log.d(TAG, "starting holdAbilities")
                holder = if (withBackgroundMovement) {
                    HolderBuilder.with(qiContext)
                        .withAutonomousAbilities(
                            AutonomousAbilitiesType.BACKGROUND_MOVEMENT,
                            AutonomousAbilitiesType.BASIC_AWARENESS
                        )
                        .build()
                } else {
                    HolderBuilder.with(qiContext)
                        .withAutonomousAbilities(
                            AutonomousAbilitiesType.BASIC_AWARENESS
                        )
                        .build()
                }
                holder!!.async().hold()
            }
        )
    }

    fun releaseAbilities(): Future<Void?>? {
        // Release the holder asynchronously.
        return if (holder != null) {

            //Log.d(TAG, "releaseAbilities");
            holder!!.async().release()
                .andThenConsume { aVoid: Void? ->
                    holder = null
                    Log.d(TAG, "stoped holdAbilities start releaseAbilities")

                }
        } else {
            //Log.d(TAG, "releaseAbilities: No holder to release");
            Future.of(null)
        }
    }


    private fun extendMap(
        explorationMap: ExplorationMap,
        qiContext: QiContext,
        updatedMapCallback: (ExplorationMap) -> Unit
    ): Future<Void> {
        Log.i(TAG.toString(), "ExtandMap Class")
        val promise = Promise<Void>().apply {
            // If something tries to cancel the associated Future, do cancel it.
            setOnCancel {
                if (!it.future.isDone) {
                    setCancelled()
                }
            }
        }

        // Create a LocalizeAndMap with the initial map, run it, and keep the Future.
        val localizeAndMapFuture = LocalizeAndMapBuilder.with(qiContext)
            .withMap(explorationMap)
            .buildAsync()
            .andThenCompose { localizeAndMap ->
                Log.i(TAG.toString(), "localizeandmap Class")

                // Add an OnStatusChangedListener to know when the robot is localized.
                localizeAndMap.addOnStatusChangedListener { status ->
                    if (status == LocalizationStatus.LOCALIZED) {
                        // Start the map notification process.
                        publishExplorationMapFuture =
                            publishExplorationMap(localizeAndMap, updatedMapCallback)
                    }
                }

                // Run the LocalizeAndMap.
                localizeAndMap.async().run()
                    .thenConsume {
                        // Remove the OnStatusChangedListener.
                        localizeAndMap.removeAllOnStatusChangedListeners()
                        // Stop the map notification process.
                        publishExplorationMapFuture?.cancel(true)
                        // In case of error, forward it to the Promise.
                        if (it.hasError() && !promise.future.isDone) {
                            promise.setError(it.errorMessage)
                        }


                    }
            }

        // Return the Future associated to the Promise.
        return promise.future.thenCompose {
            // Stop the LocalizeAndMap.
            localizeAndMapFuture.cancel(true)
            return@thenCompose it
        }
    }


    fun getThemeId(): Int? {
        try {
            return packageManager.getActivityInfo(componentName, 0).themeResource
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }


    fun setFragment(fragment: Fragment) {
        currentFragment = fragment.javaClass.simpleName
//        val topicName: String = currentFragment!!.toLowerCase().replace("fragment", "")


        Log.d(TAG, "Transaction for fragment : " + fragment.javaClass.simpleName)
        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.enter_fade_in_right, R.anim.exit_fade_out_left,
            R.anim.enter_fade_in_left, R.anim.exit_fade_out_right
        )

            transaction.replace(R.id.fragmentContainer, fragment, "currentFragment")
            transaction.addToBackStack(null)
            transaction.commit()


    }

    fun getFragment(): Fragment? {
        return supportFragmentManager.findFragmentByTag("currentFragment")
    }


    override fun onUserInteraction() {

        if (countDownNoInteraction != null) {

            if (getFragment() is SplashFragment) {
                goToRandomRunning=false
                goToFuture!!.requestCancellation()
                setFragment(MainFragment())
                countDownNoInteraction!!.start()
            } else {
                countDownNoInteraction!!.reset()

            }

        }

    }


    override fun onRobotFocusGained(qiContext: QiContext?) {

        this.qiContext = qiContext
        actuation = qiContext!!.actuation
        mapping = qiContext.mapping
        runOnUiThread {
            startMappingButton.isEnabled = true
        }

        TopicChatBuilder.qiContext = qiContext


        if (TopicChatBuilder.chat == null) {

            TopicChatBuilder.buildChat(qiContext, "chat.top")
            TopicChatBuilder.chat!!.async()
                .addOnNormalReplyFoundForListener { input ->

                    countDownNoInteraction!!.reset()

                }
        }

        TopicChatBuilder.runChat(TopicChatBuilder.chat!!)
        humanAwareness = TopicChatBuilder.qiContext!!.humanAwareness
//        humanAwareness!!.async()
//            .addOnEngagedHumanChangedListener(HumanAwareness.OnEngagedHumanChangedListener { engagedHuman: Human? ->
////            if (getFragment() is SplashFragment) {
////                if (engagedHuman != null) {
////                    setFragment(MainFragment())
////                }
////            } else {`
////                countDownNoInteraction!!.reset()
////            }
//
//
//                if (engagedHuman != null) {
//
//                    Log.d(TAG, "onRobotFocusGained: ")
//
//                } else {
//
//
//                    countDownNoInteraction!!.reset()
//
//                }
//
//            })

    }

    override fun onResume() {
        super.onResume()
        // Reset UI and variables state.
        startMappingButton.isEnabled = false
        extendMapButton.isEnabled = false
        initialExplorationMap = null
    }

    override fun onRobotFocusLost() {
        qiContext = null
        humanAwareness!!.async().removeAllOnEngagedHumanChangedListeners()

    }

    override fun onDestroy() {
        super.onDestroy()

        if (countDownNoInteraction != null) {
            countDownNoInteraction!!.cancel()
        }
        QiSDK.unregister(this, this)
    }

    override fun onPause() {
        super.onPause()
        if (countDownNoInteraction != null) {

            countDownNoInteraction!!.cancel()

        }

    }


    override fun onRobotFocusRefused(reason: String?) {

    }

}









