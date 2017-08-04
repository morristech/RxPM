package me.dmdev.rxpm.map.delegate

import android.os.Bundle
import android.view.View
import me.dmdev.rxpm.PresentationModel
import me.dmdev.rxpm.delegate.PmControllerDelegate
import me.dmdev.rxpm.map.MapPmExtension
import me.dmdev.rxpm.map.MapPmView

/**
 * @author Dmitriy Gorbunov
 */
class MapPmControllerDelegate<out PM>(private val mapPmView: MapPmView<PM>)
where PM : PresentationModel, PM : MapPmExtension {

    private val pmDelegate = PmControllerDelegate(mapPmView)
    private lateinit var mapPmViewDelegate: MapPmViewDelegate<PM>

    val presentationModel get() = pmDelegate.presentationModel

    fun onCreate() {
        pmDelegate.onCreate()
        mapPmViewDelegate = MapPmViewDelegate(pmDelegate.presentationModel, mapPmView, pmDelegate.pmBinder)
    }

    fun onCreateView(view: View, savedViewState: Bundle?) {
        mapPmViewDelegate.onCreateMapView(view, savedViewState)
        mapPmViewDelegate.onStart()
    }

    fun onAttach() {
        pmDelegate.onAttach()
        mapPmViewDelegate.onResume()
    }

    fun onDetach() {
        pmDelegate.onDetach()
        mapPmViewDelegate.onPause()
    }

    fun onSaveViewState(outState: Bundle) {
        mapPmViewDelegate.onSaveInstanceState(outState)
    }

    fun onDestroyView() {
        pmDelegate.onDestroyView()
        mapPmViewDelegate.onStop()
        mapPmViewDelegate.onDestroyMapView()
    }

    fun onDestroy() {
        pmDelegate.onDestroy()
    }

    fun onLowMemory() {
        mapPmViewDelegate.onLowMemory()
    }
}